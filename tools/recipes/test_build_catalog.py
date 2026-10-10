import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from build_catalog import build, import_recipe, canonical_id, parsed_requirements, merge_requirements

class CatalogImportTest(unittest.TestCase):
    def test_variant_and_book_ids_use_hypixel_ids(self):
        self.assertEqual('LOG:1', canonical_id('LOG-1', set()))
        self.assertEqual('ENCHANTMENT_SHARPNESS_4', canonical_id('SHARPNESS;4', {'SHARPNESS'}))
        self.assertEqual('BEE;0', canonical_id('BEE;0', {'SHARPNESS'}))

    def test_default_quantity_and_legacy_variant_grid_keep_exact_totals(self):
        r = import_recipe({'internalname': 'THING'}, {'A1': 'LOG-1', 'B2': 'LOG-1:3', 'count': 4}, 0, set())
        self.assertEqual({'LOG:1': 4}, r['ingredients'])
        self.assertEqual({'id': 'LOG:1', 'count': 1}, r['grid'][0])
        self.assertEqual(4, r['outputCount'])

    def test_parsed_unlocks_are_merged_without_duplicate_roman_numeric_gates(self):
        rows=[{'output_id':'OUTPUT','kind':'crafting','requirements':[
            {'type':'collection','name':'Gold Ingot','tier':4},
            {'type':'slayer','name':'Zombie','level':3},
            {'type':'hotm','tier':6},
            {'type':'other','text':'Unknown prerequisite'}]}]
        gates=parsed_requirements(rows)['OUTPUT']
        self.assertEqual('Requires: Gold Ingot IV & Zombie Slayer 3 & HotM 6 & Unknown prerequisite',
                         merge_requirements('Requires: Gold Ingot IV',*gates))

    def test_raw_slayer_and_reputation_codes_become_profile_gate_names(self):
        self.assertEqual('Requires: Wolf Slayer 3 & Enderman Slayer 6 & Barbarian Reputation 1000',
                         merge_requirements('WOLF_3 & EMAN_6 & BARBARIAN:1000 & Wolf Slayer III'))

    def test_invalid_counts_variant_yields_and_self_conversion_cannot_execute(self):
        for item, raw in [({'internalname': 'BEE;0'}, {'A1': 'EGG:1'}),
                          ({'internalname': 'OUTPUT'}, {'A1': 'INPUT:65'}),
                          ({'internalname': 'OUTPUT'}, {'A1': 'INPUT:1.5'}),
                          ({'internalname': 'OUTPUT'}, {'A1': 'INPUT:1', 'count': 65}),
                          ({'internalname': 'OUTPUT'}, {'A1': 'OUTPUT:1'})]:
            with self.assertRaises(ValueError):
                import_recipe(item, raw, 0, set())

    def test_every_grid_accounted_for_root_trade_recipes_and_gates_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory);(source/'items').mkdir()
            item={'internalname':'OUTPUT','displayname':'§aOutput','crafttext':'Requires: Gold Ingot IV',
                  'recipe':{'A1':'INPUT:2'},'recipes':[{'type':'trade','cost':'EMERALD:1'}]}
            (source/'items/OUTPUT.json').write_text(json.dumps(item))
            (source/'items/PET.json').write_text(json.dumps({'internalname':'BEE;0','recipe':{'A1':'EGG:1'}}))
            baseline={'names':{},'recipes':[{'key':'crafting:OUTPUT:0','kind':'CRAFT','requirement':'Requires: Combat 5'},
                {'key':'crafting:REMOVED:0','kind':'CRAFT','outputId':'REMOVED','grid':[{'id':'INPUT','count':1}],'ingredients':{'INPUT':1},'outputCount':1}]}
            with patch('build_catalog.subprocess.check_output',return_value='abc123\n'):
                result=build(source,baseline,set())
                repeated=build(source,result,set())
            self.assertEqual(result,repeated)
            self.assertFalse(any(r['outputId']=='REMOVED' for r in result['recipes']))
            self.assertEqual(2,result['coverage']['sourceCraftRows'])
            self.assertEqual(1,result['coverage']['importedCraftRows'])
            self.assertEqual(1,result['coverage']['unsupportedCraftRows'])
            self.assertEqual('Requires: Gold Ingot IV & Combat 5',result['recipes'][0]['requirement'])
            self.assertEqual('PET.json',result['unsupportedCrafts'][0]['sourceFile'])
            self.assertEqual({'A1':'EGG:1'},result['unsupportedCrafts'][0]['grid'])

if __name__ == '__main__':
    unittest.main()

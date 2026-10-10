"""Import every NEU crafting grid; account for every unsupported row explicitly.

python3 tools/recipes/build_catalog.py /path/to/NotEnoughUpdates-REPO
Only recipe facts are imported. Prices, unlock observations and GUI receipts stay live.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
RESOURCE = ROOT / 'integrations/goofyaddons/src/main/resources/goofyaddons/production-recipes.json'
SLOTS = ('A1', 'A2', 'A3', 'B1', 'B2', 'B3', 'C1', 'C2', 'C3')
ID = re.compile(r'[A-Z0-9_]+(?::[0-9]{1,2})?')

def canonical_id(value, enchants):
    match = re.fullmatch(r'([A-Z0-9_]+);([0-9]+)', value)
    if match and match[1] in enchants:
        return f'ENCHANTMENT_{match[1]}_{match[2]}'
    # NEU legacy damage variants use '-', whereas Hypixel products use ':'.
    return re.sub(r'^([A-Z0-9_]+)-([0-9]+)$', r'\1:\2', value)

def parse_stack(value, enchants):
    if value in ('', None):
        return None
    if not isinstance(value, str):
        raise ValueError('Non-text ingredient')
    raw, separator, amount = value.rpartition(':')
    if not separator:
        raw, amount = value, '1'
    product = canonical_id(raw, enchants)
    if not ID.fullmatch(product):
        raise ValueError('Variant ingredient needs an exact identity contract')
    count = float(amount)
    if not count.is_integer() or not 1 <= count <= 64:
        raise ValueError('Ingredient count cannot fit one crafting cell')
    return {'id': product, 'count': int(count)}

def import_recipe(item, raw, index, enchants):
    output = canonical_id(raw.get('overrideOutputId', item['internalname']), enchants)
    key = f"crafting:{item['internalname']}:{index}"
    if not ID.fullmatch(output):
        raise ValueError('Variant output needs exact identity/yield verification (pets and shards)')
    count = float(raw.get('count', 1))
    if not count.is_integer() or not 1 <= count <= 64:
        raise ValueError('Output count cannot fit one stack')
    grid = [parse_stack(raw.get(slot, ''), enchants) for slot in SLOTS]
    ingredients = {}
    for cell in grid:
        if cell:
            ingredients[cell['id']] = ingredients.get(cell['id'], 0) + cell['count']
    if not ingredients:
        raise ValueError('No crafting ingredients')
    if output in ingredients:
        raise ValueError('Consumes its own output; needs variant/transformation identity verification')
    return dict(key=key, kind='CRAFT', outputId=output, outputCount=int(count),
                ingredients=dict(sorted(ingredients.items())), grid=grid, durationSeconds=0,
                coins=0, requirement=item.get('crafttext', '') or '', inputPet=None)

def canonical_requirement(text):
    match=re.fullmatch(r'(ZOMBIE|SPIDER|WOLF|ENDERMAN|EMAN|BLAZE|VAMPIRE)_([0-9]+)',text.strip(),re.I)
    if match:
        names={'ZOMBIE':'Zombie','SPIDER':'Spider','WOLF':'Wolf','ENDERMAN':'Enderman','EMAN':'Enderman','BLAZE':'Blaze','VAMPIRE':'Vampire'}
        return f"{names[match[1].upper()]} Slayer {match[2]}"
    match=re.fullmatch(r'(BARBARIAN|MAGE):([0-9]+)',text.strip(),re.I)
    return f"{match[1].title()} Reputation {match[2]}" if match else text

def requirement_key(text):
    def level(match):
        values={'I':1,'V':5,'X':10,'L':50,'C':100,'D':500,'M':1000}
        total=previous=0
        for char in reversed(match[0].upper()):
            value=values[char];total+=-value if value<previous else value;previous=max(previous,value)
        return str(total)
    return re.sub(r'[^a-z0-9]', '', re.sub(r'\b[IVXLCDM]+$', level, text, flags=re.I).lower())

def parsed_requirements(rows):
    extra={}
    for row in rows:
        if row.get('kind')!='crafting':
            continue
        for gate in row.get('requirements', []):
            kind=gate.get('type')
            if kind=='collection': text=f"{gate['name']} {gate['tier']}"
            elif kind=='slayer': text=f"{gate['name']} Slayer {gate['level']}"
            elif kind=='skill': text=f"{gate['name']} {gate['level']}"
            elif kind=='hotm': text=f"HotM {gate['tier']}"
            elif kind=='reputation': text=f"{gate['faction']} Reputation {gate['amount']}"
            else: text=gate.get('text') or 'Unverified unlock'
            extra.setdefault(row['output_id'], []).append(text)
    return extra

def merge_requirements(*texts):
    parts = []
    seen = set()
    for text in texts:
        clean = re.sub(r'§.', '', text or '')
        clean = re.sub(r'^requires?:?\s*', '', clean, flags=re.I).strip()
        for part in re.split(r'\s*&\s*', clean):
            part=canonical_requirement(part)
            if part and requirement_key(part) not in seen:
                seen.add(requirement_key(part))
                parts.append(part)
    return 'Requires: ' + ' & '.join(parts) if parts else ''

def build(source, baseline, enchants, extra=None):
    extra=extra or {}
    old = {r['key']: r for r in baseline['recipes']}
    names = dict(baseline['names'])
    recipes, rejected, seen = [], [], set()
    raw_count = 0
    for file in sorted((source / 'items').glob('*.json')):
        item = json.loads(file.read_text())
        name = re.sub(r'§.', '', item.get('displayname', item['internalname']))
        names[canonical_id(item['internalname'], enchants)] = name
        rows = ([item['recipe']] if item.get('recipe') else []) + item.get('recipes', [])
        local_grids = set()
        for index, raw in enumerate(rows):
            if raw.get('type', 'crafting') != 'crafting':
                continue
            signature = json.dumps([raw.get('overrideOutputId', item['internalname']), raw.get('count', 1), [raw.get(k, '') for k in SLOTS]], sort_keys=True)
            if signature in local_grids:
                continue
            local_grids.add(signature)
            raw_count += 1
            key = f"crafting:{item['internalname']}:{index}"
            seen.add(key)
            try:
                recipe = import_recipe(item, raw, index, enchants)
                recipe['requirement'] = merge_requirements(
                    recipe['requirement'], item.get('slayer_req'), item.get('reputation_req'),
                    old.get(key, {}).get('requirement'), *extra.get(recipe['outputId'], []))
                recipes.append(recipe)
            except (ValueError, TypeError, OverflowError) as error:
                rejected.append(dict(key=key, outputId=canonical_id(raw.get('overrideOutputId', item['internalname']), enchants),
                                     name=name, reason=str(error), sourceFile=file.name, grid=raw,
                                     requirement=item.get('crafttext', '') or ''))
    # Preserve other workstations and saved keys that alias a current grid.
    # An old recipe removed upstream must not remain executable by accident.
    retained = [r for r in baseline['recipes'] if r['kind'] != 'CRAFT']
    for old_recipe in baseline['recipes']:
        if old_recipe['kind'] != 'CRAFT' or old_recipe['key'] in seen:
            continue
        match = next((r for r in recipes if all(r.get(k)==old_recipe.get(k)
                     for k in ('outputId','outputCount','ingredients','grid'))), None)
        if match:
            retained.append({**old_recipe, 'requirement': merge_requirements(old_recipe.get('requirement'), match['requirement'])})
    gates=json.loads((ROOT/'tools/bazaar-calc/product-requirements.json').read_text())['products']
    recipes = sorted(recipes + retained, key=lambda r: r['key'])
    for recipe in recipes:
        requirements=[gates[id] for id in sorted({recipe['outputId'],*recipe['ingredients']}) if id in gates]
        recipe['requirement']=merge_requirements(recipe.get('requirement'),*requirements)
    if len({r['key'] for r in recipes}) != len(recipes):
        raise ValueError('Duplicate recipe keys')
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip()
    return {**baseline, 'source': f'https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/tree/{revision}',
            'sourceRevision': revision, 'names': dict(sorted(names.items())), 'recipes': recipes,
            'rejectedRecipes': len(rejected), 'unsupportedCrafts': rejected,
            'coverage': dict(sourceCraftRows=raw_count, importedCraftRows=raw_count-len(rejected),
                             unsupportedCraftRows=len(rejected), retainedCraftRows=sum(r['kind']=='CRAFT' for r in retained)),
            'requirementSource': 'NEU crafttext/slayer/reputation, parsed calculator requirements and existing reviewed gates; unresolved requirements block execution.'}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--output', type=Path, default=RESOURCE)
    args = parser.parse_args()
    baseline = json.loads(RESOURCE.read_text())
    rules = json.loads((ROOT / 'tools/bazaar-calc/licenses/enchants.json').read_text())['rules']
    enchants = {name.removeprefix('ENCHANTMENT_') for name in rules}
    rows=json.loads((ROOT / 'tools/bazaar-calc/calculator/data/recipes.json').read_text())
    catalog = build(args.source, baseline, enchants, parsed_requirements(rows))
    args.output.write_text(json.dumps(catalog, separators=(',', ':'), ensure_ascii=False)+'\n')
    print(json.dumps(catalog['coverage'], indent=2))

if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Extract normalized recipes, not NBT or commands, from a pinned NEU item checkout."""
import argparse, json, re, subprocess
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('checkout',type=Path)
parser.add_argument('--output',type=Path,default=Path('src/main/resources/goofyaddons/production-recipes.json'))
args=parser.parse_args()
id_pattern=re.compile(r'[A-Z0-9_]+(?:;[0-6])?')
def ingredient(value):
    item,count=value.rsplit(':',1)
    count=int(count)
    if not id_pattern.fullmatch(item) or not 1<=count<=(10000000000000 if item=='SKYBLOCK_COIN' else 1000000): raise ValueError('Invalid ingredient')
    return {'id':item,'count':count}
rows=[];names={};skipped=0
for path in sorted((args.checkout/'items').glob('*.json')):
    item=json.loads(path.read_text());output=item.get('internalname',path.stem)
    if not id_pattern.fullmatch(output): continue
    names[output]=re.sub('§.', '',item.get('displayname',output))
    recipes=list(item.get('recipes',[]))
    if item.get('recipe'): recipes.insert(0,{'type':'crafting','count':item.get('count',1),**item['recipe']})
    for index,recipe in enumerate(recipes):
        kind=recipe.get('type')
        if kind not in ['crafting','forge','katgrade']:continue
        try:
            target=recipe.get('overrideOutputId',recipe.get('output',output))
            if not id_pattern.fullmatch(target):raise ValueError('Invalid output')
            grid=[ingredient(recipe[key]) if recipe.get(key) else None for key in ['A1','A2','A3','B1','B2','B3','C1','C2','C3']] if kind=='crafting' else []
            inputs=[x for x in grid if x] if grid else [ingredient(x) for x in recipe.get('inputs',recipe.get('items',[]))]
            pet=recipe.get('input') if kind=='katgrade' else None
            if pet: inputs.append({'id':pet,'count':1})
            totals={}
            for value in inputs:totals[value['id']]=totals.get(value['id'],0)+value['count']
            if not totals or target in totals:raise ValueError('Empty/self recipe')
            count=int(recipe.get('count',1));seconds=int(recipe.get('duration',recipe.get('time',0)));coins=float(recipe.get('coins',0))+totals.pop('SKYBLOCK_COIN',0)
            if not 1<=count<=64 or not 0<=seconds<=31536000 or not 0<=coins<=1e13:raise ValueError('Invalid quantities')
            if grid and (any(x and x['count']>64 for x in grid) or ';' in target or any(';' in x for x in totals)):raise ValueError('Variant-sensitive crafting unsupported')
            if kind=='katgrade' and not re.fullmatch(r'[A-Z0-9_]+;[0-6]',pet or ''):raise ValueError('Invalid pet input')
            rows.append({'key':f'{kind}:{target}:{index}','kind':{'crafting':'CRAFT','forge':'FORGE','katgrade':'KAT'}[kind],
                'outputId':target,'outputCount':count,'ingredients':totals,'grid':grid,'durationSeconds':seconds,'coins':coins,
                'requirement':item.get('crafttext',''),'inputPet':pet})
        except (ValueError,TypeError,KeyError):skipped+=1
commit=subprocess.check_output(['git','-C',str(args.checkout),'rev-parse','HEAD'],text=True).strip()
result={'schema':1,'source':f'https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/tree/{commit}',
    'license':'MIT; see NEU-CATALOG-LICENSE.txt','names':names,'recipes':rows,'rejectedRecipes':skipped}
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text(json.dumps(result,separators=(',',':'))+'\n')
from collections import Counter
print(dict(Counter(r['kind'] for r in rows)), 'rejected',skipped)

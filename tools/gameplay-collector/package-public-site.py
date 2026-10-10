"""Package the public Cloudflare website and its API; exclude all owner credentials."""
from pathlib import Path
import zipfile
import argparse
import json
import re

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--database-id')
parser.add_argument('--database-name')
parser.add_argument('--preserve-config', action='store_true', help='Ship wrangler.example.jsonc without overwriting an existing deployment config')
parser.add_argument('--output', default='goofyaddons-public-website.zip')
args = parser.parse_args()
if bool(args.database_id) != bool(args.database_name):
    parser.error('Provide both database ID and name, or neither')
if args.database_id and not re.fullmatch(r'[a-f0-9-]{36}', args.database_id):
    parser.error('Invalid D1 database ID')
if Path(args.output).name != args.output:
    parser.error('Output must be a ZIP filename')
output = root / 'dist' / args.output
files = ['worker.mjs', 'profile-lookup.mjs', 'profile-levels.mjs', 'publishing-status.mjs', 'public-market.mjs', 'public-crafts.mjs', 'ah-history.mjs', 'scheduled-ah.mjs', 'NEU-LICENSE', 'wrangler.jsonc']
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in files:
        target = f'tools/gameplay-collector/{"wrangler.example.jsonc" if name == "wrangler.jsonc" and args.preserve_config else name}'
        if name == 'wrangler.jsonc' and args.database_id:
            config = json.loads((root / 'tools/gameplay-collector' / name).read_text())
            config['d1_databases'][0].update(database_id=args.database_id, database_name=args.database_name)
            archive.writestr(target, json.dumps(config, indent=2) + '\n')
        else:
            archive.write(root / 'tools/gameplay-collector' / name, target)
    for name in ['community-protocol.mjs', 'craft-market.mjs']:
        archive.write(root / 'tools/bazaar-calc' / name, 'tools/bazaar-calc/' + name)
    for path in sorted((root / 'tools/bazaar-calc/calculator').rglob('*')):
        if path.is_file():
            archive.write(path, path.relative_to(root).as_posix())
    archive.write(root / 'docs/PUBLIC-WEBSITE.md', 'START-HERE.md')
print(output)

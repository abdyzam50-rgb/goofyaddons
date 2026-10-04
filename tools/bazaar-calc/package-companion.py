"""Package a clean companion; local observations and account data are excluded."""
from pathlib import Path
import sys
import zipfile
root = Path(__file__).resolve().parent
version = sys.argv[1]
if not version or any(c not in '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-' for c in version):
    raise ValueError('Invalid version')
output = root.parents[1] / 'dist' / f'goofyaddons-bazaar-calc-{version}.zip'
output.parent.mkdir(exist_ok=True)
files = ['data-paths.mjs', 'server.mjs', 'collector.mjs', 'collect-market.mjs', 'execution-history.mjs', 'adapter.mjs', 'engine.mjs',
         'dashboard-state.mjs', 'dashboard-profit.mjs', 'history.json.gz', 'provenance.json', 'README.md', 'update-history.mjs']
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in files:
        archive.write(root / name, f'bazaar-calc/{name}')
    for folder in ['dashboard', 'licenses', 'windows']:
        for path in sorted((root / folder).rglob('*')):
            if path.is_file():
                archive.write(path, f'bazaar-calc/{path.relative_to(root).as_posix()}')
print(output)

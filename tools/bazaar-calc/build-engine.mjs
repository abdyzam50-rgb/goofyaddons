// Rebuild the vendored calculator engine from an already built, pinned upstream checkout.
import { pathToFileURL, fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { readFileSync, writeFileSync, mkdirSync, copyFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { gzipSync } from 'node:zlib';
const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(process.argv[2] ?? '/tmp/bazaar-calc-review');
const data = resolve(process.argv[3] ?? '/workspace/.setup/bazaar-calc-site-data');
const commit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repo, encoding: 'utf8' }).trim();
const expected = '51268005376496bf993e0c1934b8a7e44656b0bc';
if (commit !== expected) throw new Error('Upstream checkout does not match the reviewed commit');
// The pin is recorded in provenance; updates require reviewing upstream behavior and rebuilding tests.
const esbuild = await import(pathToFileURL(resolve(repo, 'node_modules/.pnpm/esbuild@0.21.5/node_modules/esbuild/lib/main.js')));
const exports = ['assembleMarket', 'quotesFromBazaar', 'buyLeg', 'sellLeg', 'evaluate', 'DEFAULT_SETTINGS', 'DEFAULT_PROFILE',
  'enchantRules', 'booksNeeded', 'combineXpCost', 'parseBookId', 'actionSeconds', 'curve', 'at', 'seriousFlags', 'TopTracker', 'summarizeTop', 'bookFlow', 'toLevels', 'validateBazaar', 'degradedBazaar', 'counterTrades'];
// These pure exports are copied verbatim; importing stats.js would initialize PostgreSQL.
const statsSource = readFileSync(resolve(repo, 'packages/server-core/dist/stats.js'), 'utf8');
const pureStats = statsSource.slice(statsSource.indexOf('export function competition('), statsSource.indexOf('export async function computeStats('));
if (!pureStats.includes('export function delists(') || pureStats.includes('import ')) throw new Error('Unexpected upstream statistics layout');
const entry = `export { ${exports.join(', ')} } from ${JSON.stringify(resolve(repo, 'packages/shared/dist/index.js'))};\n${pureStats}`;
await esbuild.build({ stdin: { contents: entry, resolveDir: repo }, outfile: resolve(here, 'engine.mjs'),
  bundle: true, platform: 'node', format: 'esm', target: 'node22', legalComments: 'inline' });
const source = JSON.parse(readFileSync(resolve(data, 'market.json'), 'utf8'));
writeFileSync(resolve(here, 'history.json.gz'), gzipSync(JSON.stringify({ asOf: source.asOf, stats: source.stats,
  hold: source.hold, names: source.names }), { level: 9 }));
mkdirSync(resolve(here, 'licenses'), { recursive: true });
mkdirSync(resolve(here, 'dashboard'), { recursive: true });
copyFileSync(resolve(repo, 'packages/web/src/styles.css'), resolve(here, 'dashboard/upstream.css'));
for (const name of ['LICENSE', 'NOTICE.md']) copyFileSync(resolve(repo, name), resolve(here, 'licenses', `BAZAAR-CALC-${name}`));
copyFileSync(resolve(repo, 'packages/shared/src/rules/enchants.json'), resolve(here, 'licenses/enchants.json'));
writeFileSync(resolve(here, 'provenance.json'), JSON.stringify({ repository: 'https://github.com/Goofythesecond/bazaar-calc',
  commit, historyAsOf: source.asOf, builtAt: Date.now(), engine: 'upstream shared engine, order-only adapter',
  historySource: 'upstream contribution files, scripts/data/build-site.mjs --offline' }, null, 2) + '\n');
// Native execution must use the same reviewed combine rules as the calculator.
const catalogPath = resolve(here, 'automatic-products.json');
const catalog = JSON.parse(readFileSync(catalogPath, 'utf8'));
const native = await import(pathToFileURL(resolve(here, 'engine.mjs')));
catalog.books = {};
for (const rule of Object.values(native.enchantRules())) {
  if (rule.combine_status !== 'combinable' || !rule.combine_cap) continue;
  const routes = [];
  for (let from = 1; from < Math.min(10, rule.combine_cap); from++) {
    for (let to = from + 1; to <= Math.min(10, rule.combine_cap); to++) {
      if (native.booksNeeded(rule, from, to) &&
          Array.from({length: to - from}, (_, i) => native.combineXpCost(rule, from + i)).every(x => x === 0)) routes.push([from, to]);
    }
  }
  if (routes.length) catalog.books[rule.id] = {name: rule.name, routes};
}
writeFileSync(catalogPath, JSON.stringify(catalog, null, 2).replace(/[\u0080-\uffff]/g, c => '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0')) + '\n');
console.log(`Bundled Bazaar Calc ${commit}; history ${new Date(source.asOf).toISOString()}`);

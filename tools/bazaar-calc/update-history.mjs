// Import newly derived upstream market.json without changing its source timestamp or engine pin.
import { readFileSync, writeFileSync, renameSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
const source = process.argv[2];
if (!source) throw new Error('Usage: node update-history.mjs <upstream site-data/market.json>');
const data = JSON.parse(readFileSync(source, 'utf8'));
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
if (!Number.isFinite(data.asOf) || data.asOf <= 0 || data.asOf > Date.now() + 5000
  || !object(data.stats) || !object(data.hold) || !object(data.names)) throw new Error('Invalid upstream market statistics');
const destination = new URL('./history.json.gz', import.meta.url), temporary = new URL('./history.json.gz.tmp', import.meta.url);
writeFileSync(temporary, gzipSync(JSON.stringify({ asOf: data.asOf, stats: data.stats, hold: data.hold, names: data.names }), { level: 9 }));
renameSync(temporary, destination);
console.log(`Imported history as of ${new Date(data.asOf).toISOString()}; restart the companion to load it.`);

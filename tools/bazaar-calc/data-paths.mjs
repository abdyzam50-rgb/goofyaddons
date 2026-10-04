// Persistent user data lives outside the replaceable companion installation.
import { homedir } from 'node:os';
import { resolve, join, isAbsolute } from 'node:path';
import { mkdirSync, existsSync, copyFileSync, constants, linkSync, unlinkSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
export const LEGACY_DIRECTORY=fileURLToPath(new URL('./data/',import.meta.url));
const FILES=['live-history.json.gz','execution-history.json','collection-status.json','companion.log','companion-error.log','companion.log.previous','companion-error.log.previous'];
export function resolveDataDirectory({platform=process.platform,env=process.env,home=homedir()}={}) {
  if(env.GOOFY_BAZAAR_DATA_DIR) {
    if(!isAbsolute(env.GOOFY_BAZAAR_DATA_DIR))throw new Error('GOOFY_BAZAAR_DATA_DIR must be an absolute path');
    return resolve(env.GOOFY_BAZAAR_DATA_DIR);
  }
  const base=platform==='win32' ? env.LOCALAPPDATA || join(home,'AppData','Local')
    : platform==='darwin' ? join(home,'Library','Application Support')
    : env.XDG_DATA_HOME && isAbsolute(env.XDG_DATA_HOME) ? env.XDG_DATA_HOME : join(home,'.local','share');
  return join(base,'GoofyAddons','bazaar-calc');
}
export function prepareDataDirectory(directory,{legacy=LEGACY_DIRECTORY,warn=message=>console.warn(message)}={}) {
  mkdirSync(directory,{recursive:true});
  if(resolve(directory)===resolve(legacy))return directory;
  for(const name of FILES) {
    const source=join(legacy,name),target=join(directory,name);
    if(!existsSync(source))continue;
    if(existsSync(target))continue; // Established external history always wins.
    const temp=join(directory,`.migration-${randomUUID()}.tmp`);
    try {copyFileSync(source,temp,constants.COPYFILE_EXCL);linkSync(temp,target);}
    catch(error){if(error.code==='EEXIST')continue;throw new Error(`Cannot migrate ${name}; original preserved at ${source}: ${error.message}`);}
    finally {if(existsSync(temp))unlinkSync(temp);}
    warn(`Migrated ${name} to ${target}; original retained.`);
  }
  return directory;
}
let prepared;
export function dataDirectory() {return prepared ??= prepareDataDirectory(resolveDataDirectory());}
export function dataFile(name) {
  if(!FILES.includes(name))throw new Error('Unsupported data filename');
  return join(dataDirectory(),name);
}
if(process.argv[1]===fileURLToPath(import.meta.url)) {
  if(process.argv.length>2) {
    if(process.argv.length!==4 || process.argv[2]!=='--migrate-from')throw new Error('Usage: node data-paths.mjs [--migrate-from OLD_DATA_FOLDER]');
    const legacy=resolve(process.argv[3]);
    if(!existsSync(legacy))throw new Error(`Old data folder does not exist: ${legacy}`);
    prepareDataDirectory(resolveDataDirectory(),{legacy});
  }
  console.log(dataDirectory());
}

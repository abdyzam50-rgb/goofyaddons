// Owner-only enrollment helper. Raw contributor keys are written outside the checkout.
import {randomBytes,createHash} from 'node:crypto';
import {mkdirSync,readFileSync,writeFileSync,renameSync,existsSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {dataDirectory} from '../bazaar-calc/data-paths.mjs';
export function enroll(directory,label,{revoke=false}={}) {
 if(!/^[a-zA-Z0-9_-]{1,40}$/.test(label))throw new Error('Use a short enrollment label containing letters, numbers, hyphens or underscores');
 mkdirSync(directory,{recursive:true,mode:0o700});const registry=join(directory,'enrollments.json');
 const records=existsSync(registry)?JSON.parse(readFileSync(registry,'utf8')):{};
 if(!records||Array.isArray(records)||Object.values(records).some(h=>typeof h!=='string'||!/^[a-f0-9]{64}$/.test(h)))throw new Error('Enrollment registry unreadable; preserved');
 const keyFile=join(directory,`${label}.key`);
 if(revoke){if(!records[label])throw new Error('Unknown enrollment');delete records[label];}
 else {
  if(records[label]||existsSync(keyFile))throw new Error('Label already used; choose a new label');
  const token=randomBytes(32).toString('base64url');
  writeFileSync(keyFile,token+'\n',{mode:0o600,flag:'wx'});
  records[label]=createHash('sha256').update(token).digest('hex');
 }
 writeFileSync(`${registry}.tmp`,JSON.stringify(records),{mode:0o600});renameSync(`${registry}.tmp`,registry);
 const hashesFile=join(directory,'contributor-hashes.json');writeFileSync(hashesFile,JSON.stringify(Object.values(records)),{mode:0o600});
 return {keyFile:revoke?null:keyFile,hashesFile};
}
if(process.argv[1]===fileURLToPath(import.meta.url)) {
 const [operation,label]=process.argv.slice(2);
 if(!['add','revoke'].includes(operation))throw new Error('Usage: node enroll.mjs add tester-label OR node enroll.mjs revoke tester-label');
 const result=enroll(join(dataDirectory(),'collector-enrollment'),label,{revoke:operation==='revoke'});
 if(result.keyFile)console.log(`Give this private key file only to the enrolled tester: ${result.keyFile}`);
 console.log(`Update the Worker's CONTRIBUTOR_HASHES secret using: ${result.hashesFile}`);
}

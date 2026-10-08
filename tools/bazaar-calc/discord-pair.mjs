// Creates pairing material locally, outside the replaceable companion folder.
import { randomBytes } from 'node:crypto';
import { mkdirSync,writeFileSync,existsSync,readFileSync } from 'node:fs';
import { resolve,join } from 'node:path';
import { dataFile } from './data-paths.mjs';
if(process.argv.length!==3)throw new Error('Usage: node discord-pair.mjs MINECRAFT_CONFIG_FOLDER');
const folder=resolve(process.argv[2]);mkdirSync(folder,{recursive:true});
const file=join(folder,'goofyaddons-discord.key');
let key;
if(existsSync(file)){key=readFileSync(file,'utf8').trim();if(!/^[a-f0-9]{64}$/.test(key))throw new Error('Existing pairing file invalid; original preserved');}
else {key=randomBytes(32).toString('hex');writeFileSync(file,key+'\n',{flag:'wx',mode:0o600});}
const settings=dataFile('discord-settings.json');
if(!existsSync(settings))writeFileSync(settings,JSON.stringify({enabled:false,botToken:'',channelId:'',ownerId:'',webhookUrl:'',bridgeKey:key,statusSeconds:300},null,2)+'\n',{flag:'wx',mode:0o600});
console.log(`Minecraft pairing file: ${file}\nPrivate Discord settings: ${settings}\nEdit those settings, then restart server.mjs. Existing files are never overwritten. Keep both files private.`);

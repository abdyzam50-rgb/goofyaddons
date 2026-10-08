import { existsSync,readFileSync } from 'node:fs';
import { dataFile } from './data-paths.mjs';
export function discordSettings(env=process.env) {
  if(env.GOOFY_DISCORD_BOT_TOKEN)return {botToken:env.GOOFY_DISCORD_BOT_TOKEN,channelId:env.GOOFY_DISCORD_CHANNEL_ID,
    ownerId:env.GOOFY_DISCORD_OWNER_ID,webhookUrl:env.GOOFY_DISCORD_WEBHOOK_URL,bridgeKey:env.GOOFY_DISCORD_BRIDGE_KEY};
  const path=dataFile('discord-settings.json');if(!existsSync(path))return null;
  const settings=JSON.parse(readFileSync(path,'utf8'));return settings.enabled===true?settings:null;
}

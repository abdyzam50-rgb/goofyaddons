// Bounded public-market collection for scheduled jobs. Does not read account data.
import { dataFile } from './data-paths.mjs';
import { MarketCollector } from './collector.mjs';
import { fileURLToPath } from 'node:url';
import { writeFile } from 'node:fs/promises';
export async function collectWindow({collector=new MarketCollector(), durationMs=600000, wait=ms=>new Promise(r=>setTimeout(r,ms)), now=Date.now}={}) {
  const started=now(), before=collector.state.asOf;
  try {
    do {
      await collector.poll();
      const left=durationMs-(now()-started);
      if(left<=0)break;
      await wait(Math.min(left,Math.min(120000,collector.intervalMs*Math.max(1,2**Math.min(3,collector.failures)))));
    } while(now()-started<durationMs);
  } finally { await collector.stop(); }
  if(collector.state.asOf<=before || collector.storageError)throw new Error(collector.storageError ?? collector.error ?? 'No new market observations accepted');
  return collector.status();
}
if(process.argv[1]===fileURLToPath(import.meta.url)) {
  const minutes=Number(process.argv[2]??10);
  if(!Number.isFinite(minutes)||minutes<0.1||minutes>60)throw new Error('Duration must be 0.1–60 minutes');
  const status=await collectWindow({durationMs:minutes*60000});
  await writeFile(dataFile('collection-status.json'),JSON.stringify(status,null,2));
  console.log(JSON.stringify(status));
}

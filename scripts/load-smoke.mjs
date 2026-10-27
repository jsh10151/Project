import {performance} from 'node:perf_hooks';
const base=process.env.LOAD_BASE_URL||'http://127.0.0.1:8081';
const parsed=new URL(base);if(!['localhost','127.0.0.1','[::1]'].includes(parsed.hostname))throw new Error('This smoke test only targets localhost.');
const total=200,concurrency=10,latencies=[];let next=0,failures=0;
const start=performance.now();
await Promise.all(Array.from({length:concurrency},async()=>{
 while(true){const index=next++;if(index>=total)return;const before=performance.now();
  try{const result=await fetch(base+(index%2?'/api/spaces/1':'/api/spaces'),{signal:AbortSignal.timeout(10000)});if(!result.ok)throw new Error('HTTP '+result.status);await result.json();}catch{failures++;}finally{latencies.push(performance.now()-before);}
 }
}));
latencies.sort((a,b)=>a-b);const elapsed=(performance.now()-start)/1000;
console.log(JSON.stringify({scope:'read-only local smoke; not capacity benchmark',requests:total,concurrency,failures,seconds:+elapsed.toFixed(2),requestsPerSecond:+(total/elapsed).toFixed(1),p50Ms:+latencies[Math.ceil(total*.5)-1].toFixed(1),p95Ms:+latencies[Math.ceil(total*.95)-1].toFixed(1)},null,2));
if(failures)process.exitCode=1;
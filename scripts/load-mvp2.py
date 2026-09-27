#!/usr/bin/env python3
"""Small reproducible read-only local load measurement; no production capacity claim."""
import concurrent.futures,json,time,urllib.request,urllib.error,platform,os,argparse,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--seconds',type=int,default=20);p.add_argument('--concurrency',type=int,default=4);args=p.parse_args()
session=json.loads(Path('.local/session.json').read_text());token=session['users'][1]['access_token'];deadline=time.monotonic()+args.seconds
# Round-robin current policy, private image and message-history reads.
paths=['/api/v1/posts/'+session['postId'],'/api/v1/media/'+session['mediaId']+'/content','/api/v1/conversations/'+session['conversationId']+'/messages']
def worker():
 latencies=[];errors=0
 while time.monotonic()<deadline:
  start=time.perf_counter()
  try:
   with urllib.request.urlopen(urllib.request.Request('http://localhost:8080'+paths[len(latencies)%len(paths)],headers={'Authorization':'Bearer '+token}),timeout=5) as r:assert r.status==200;r.read()
  except Exception:errors+=1
  latencies.append((time.perf_counter()-start)*1000)
 return latencies,errors
started=time.monotonic()
with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:results=list(pool.map(lambda _:worker(),range(args.concurrency)))
elapsed=time.monotonic()-started;latencies=sorted(x for r in results for x in r[0]);errors=sum(r[1] for r in results)
def percentile(p):return latencies[min(len(latencies)-1,int(len(latencies)*p))]
report={'host':platform.platform(),'architecture':platform.machine(),'dockerResources':subprocess.check_output(['docker','info','--format','{{.NCPU}} CPUs / {{.MemTotal}} bytes'],text=True).strip(),'dataset':'three members and moderator; one connected pair, private post with one PNG, one conversation; round-robin post/media/message reads','replicas':{name:1 for name in ['gateway','member','content','notification','media','messaging']},'concurrency':args.concurrency,'durationSeconds':elapsed,'requests':len(latencies),'requestsPerSecond':len(latencies)/elapsed,'errors':errors,'p50Ms':percentile(.5),'p95Ms':percentile(.95),'p99Ms':percentile(.99),'resourceSnapshot':subprocess.check_output(['docker','stats','--no-stream','--format','{{.Name}} CPU={{.CPUPerc}} MEM={{.MemUsage}}'],text=True).splitlines(),'limitations':'Local short read-only run with tracing enabled; not a saturation test or capacity guarantee.'}
Path('docs/mvp2-load-result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:report[k] for k in ['requests','requestsPerSecond','errors','p95Ms']},indent=2));assert errors==0

#!/usr/bin/env python3
"""Bounded local hiring workload; explicit fixture sizes and separate operation measurements."""
from hiring_http import *
import argparse,concurrent.futures,platform,subprocess,threading
p=argparse.ArgumentParser();p.add_argument('--seconds',type=int,default=20);p.add_argument('--concurrency',type=int,default=4);p.add_argument('--jobs',type=int,default=60);args=p.parse_args()
assert 1<=args.jobs<=500 and 1<=args.concurrency<=16 and 1<=args.seconds<=120
profiles();o=users['owner'];a=users['alice'];c=company(o);jobs=[job(o,c['id'],'Load engineer '+str(i)) for i in range(args.jobs)]
report={'host':platform.platform(),'architecture':platform.machine(),'dockerResources':subprocess.check_output(['docker','info','--format','{{.NCPU}} CPUs / {{.MemTotal}} bytes'],text=True).strip(),'dataset':{'fixtureCompanies':1,'publishedJobs':args.jobs,'applicants':1,'profileExperiences':1},'replicas':{s:1 for s in ['gateway','member','content','notification','media','messaging','hiring']},'concurrency':args.concurrency,'operations':{}}
def measured(fn,work):
 start=time.monotonic()
 with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:results=list(pool.map(fn,work))
 elapsed=time.monotonic()-start;lats=sorted(t for r in results for t in r[0]);errors=sum(r[1] for r in results)
 return {'durationSeconds':elapsed,'requests':len(lats),'requestsPerSecond':len(lats)/elapsed,'errors':errors,'p50Ms':lats[int(len(lats)*.5)],'p95Ms':lats[min(len(lats)-1,int(len(lats)*.95))],'p99Ms':lats[min(len(lats)-1,int(len(lats)*.99))]}
def submit(j):
 start=time.perf_counter();code,_=call('POST','/api/v1/applications',a,{'jobId':j['id'],'idempotencyKey':str(uuid.uuid4()),'coverNote':'Load fixture'});return [(time.perf_counter()-start)*1000],int(code!=200)
report['operations']['submission']=measured(submit,jobs)
for name,path,u in [('search','/api/v1/jobs?companyId='+c['id']+'&q=engineer&workArrangement=REMOTE&size=20',a),('recruiterListing','/api/v1/companies/'+c['id']+'/applications?size=20',o)]:
 deadline=time.monotonic()+args.seconds
 def worker(_):
  latencies=[];errors=0
  while time.monotonic()<deadline:
   start=time.perf_counter()
   try:code,_=call('GET',path,u);errors+=int(code!=200)
   except OSError:errors+=1
   latencies.append((time.perf_counter()-start)*1000)
  return latencies,errors
 report['operations'][name]=measured(worker,range(args.concurrency))
report['resourceSnapshot']=subprocess.check_output(['docker','stats','--no-stream','--format','{{.Name}} CPU={{.CPUPerc}} MEM={{.MemUsage}}'],text=True).splitlines()
report['limitations']='Small isolated company on shared local infrastructure, tracing enabled; other regression fixtures remain. Submission is a fixed-size burst; reads are duration-based. No saturation, production capacity or full-text index claim. Company locks serialize writes and authorized recruiter queries.'
Path('docs/mvp3-load-result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report['operations'],indent=2));assert all(v['errors']==0 for v in report['operations'].values())

#!/usr/bin/env python3
from mvp4_support import *
import platform,concurrent.futures,statistics
refresh();profiles();a=users['alice'];b=users['bob'];o=users['owner']
for u,v in [(a,b),(b,a)]:ok('DELETE','/api/v1/blocks/'+v['id'],u,expected=204)
ok('PUT','/api/v1/members/'+b['id']+'/follow',a,expected=204)
for i in range(100):ok('POST','/api/v1/posts',b,{'body':'MVP4 load fixture '+str(i),'visibility':'MEMBERS'})
latencies=[];errors=[];by_path={};end=time.monotonic()+20;start=time.monotonic()
paths=['/api/v1/feed?size=20','/api/v1/members/suggestions?size=10','/api/v1/companies/suggestions?industry=Software&size=10']
def worker(index):
 local=[]
 while time.monotonic()<end:
  at=time.perf_counter();status,_=call('GET',paths[index%3],a);local.append((time.perf_counter()-at)*1000)
  if status!=200:errors.append(status)
 return paths[index%3],local
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 for path,result in pool.map(worker,range(4)):
  latencies.extend(result);by_path.setdefault(path,[]).extend(result)
elapsed=time.monotonic()-start;latencies.sort();assert not errors,errors
for s in ok('GET','/api/v1/jobs/searches',a):ok('DELETE','/api/v1/jobs/searches/'+s['id'],a,expected=204)
c=company(o);ok('PUT','/api/v1/notifications/preferences/job-alerts',a,{'enabled':True},expected=204)
ok('POST','/api/v1/jobs/searches',a,{'name':'Load alerts','keywords':'engineer','companyIds':[c['id']],'alertsEnabled':True})
alert_latencies=[]
for i in range(5):
 at=time.monotonic();j=job(o,c['id']);wait(lambda:count(a,j['id'])==1,'measured alert');alert_latencies.append((time.monotonic()-at)*1000)
endpoint_stats={}
for path,values in by_path.items():
 values.sort();endpoint_stats[path]={'requests':len(values),'p50Ms':values[int(len(values)*.5)],'p95Ms':values[int(len(values)*.95)],'p99Ms':values[int(len(values)*.99)]}
trace_agents={}
for service in ['api-gateway','member-service','content-service','hiring-service','notification-service']:
 container=subprocess.check_output(['docker','compose','ps','-q',service],text=True).strip()
 config=json.loads(subprocess.check_output(['docker','inspect',container],text=True))[0]
 trace_agents[service]=any('-javaagent:' in entry for entry in config['Config']['Env'] if entry.startswith('JAVA_TOOL_OPTIONS='))
report={'hardware':platform.platform(),'architecture':platform.machine(),'dockerResources':subprocess.check_output(['docker','info','--format','{{.NCPU}} CPUs / {{.MemTotal}} bytes'],text=True).strip(),'dataset':{'newFeedPosts':100,'realActors':3,'publishedMatchingJobs':5,'explicitMatchingSearches':1,'retainedOtherTestData':True,'totalPosts':int(sql('SELECT COUNT(*) FROM content_app.posts;')),'totalProfiles':int(sql('SELECT COUNT(*) FROM member_app.members;')),'activeSavedSearches':int(sql('SELECT COUNT(*) FROM hiring_app.saved_searches WHERE deleted=0 AND enabled=1;')),'activeSearchOwners':int(sql('SELECT COUNT(DISTINCT member_id) FROM hiring_app.saved_searches WHERE deleted=0 AND enabled=1;'))},'traceAgentEnabled':any(trace_agents.values()),'traceAgentsByService':trace_agents,'endpoints':endpoint_stats,'replicas':{s:1 for s in ['gateway','member','content','hiring','notification']},'concurrency':4,'durationSeconds':elapsed,'requests':len(latencies),'requestsPerSecond':len(latencies)/elapsed,'p50Ms':latencies[int(len(latencies)*.5)],'p95Ms':latencies[int(len(latencies)*.95)],'p99Ms':latencies[int(len(latencies)*.99)],'errors':errors,'publicationToAlertMs':alert_latencies,'resourceSnapshot':subprocess.check_output(['docker','stats','--no-stream','--format','{{.Name}} CPU={{.CPUPerc}} MEM={{.MemUsage}}'],text=True).splitlines(),'limitations':'Local mixed warm read workload; publication metric includes draft creation and polling resolution250ms. Not saturation or production capacity.'}
Path(os.getenv('MVP4_LOAD_EVIDENCE','docs/mvp4-load-evidence.json')).write_text(json.dumps(report,indent=2)+'\n');print('PASS measured bounded feed/discovery reads and publication-to-alert latency',json.dumps(report))

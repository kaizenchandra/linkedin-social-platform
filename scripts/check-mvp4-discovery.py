#!/usr/bin/env python3
from hiring_http import *
import platform,subprocess,concurrent.futures,os
profiles();a=users['alice'];b=users['bob'];c=users['outsider'];owner=users['owner']
for x,y in [(a,c),(c,a),(b,c),(c,b)]:ok('DELETE','/api/v1/blocks/'+y['id'],x,expected=204)
ok('DELETE','/api/v1/members/'+c['id']+'/follow',a,expected=204)
# A-B was established by the feed journey; add B-C, allowing existing accepted fixture.
status,r=call('POST','/api/v1/connections',b,{'targetId':c['id']})
if status==200:ok('POST','/api/v1/connections/'+r['id']+'/accept',c)
else:assert status==409,(status,r)
suggestions=ok('GET','/api/v1/members/suggestions?size=20',a)
assert any(x['memberId']==c['id'] and x['reason']=='Connections in common' for x in suggestions)
assert not any(x['memberId'] in [a['id'],b['id']] for x in suggestions)
assert suggestions==ok('GET','/api/v1/members/suggestions?size=20',a)
ok('PUT','/api/v1/members/'+c['id']+'/follow',a,expected=204)
assert not any(x['memberId']==c['id'] for x in ok('GET','/api/v1/members/suggestions?size=20',a))
industry='Discovery-'+uuid.uuid4().hex
companies=[]
for i in range(2):companies.append(ok('POST','/api/v1/companies',owner,{'displayName':'Discovery company','slug':'discovery-'+uuid.uuid4().hex,'description':'Explicit','industry':industry,'location':'London'}))
ok('PUT','/api/v1/companies/'+companies[0]['id']+'/follow',a,expected=204)
path='/api/v1/companies/suggestions?industry='+industry+'&location=London'
result=ok('GET',path,a);assert [x['companyId'] for x in result]==[companies[1]['id']]
assert result[0]['reason']=='Industry matches; Location matches'
j=job(owner,companies[0]['id']);other=job(owner,companies[1]['id'])
ids=[x['id'] for x in ok('GET','/api/v1/jobs?followedCompanies=true&size=100',a)['items']]
assert j['id'] in ids and other['id'] not in ids
paths=['/api/v1/members/suggestions?size=20',path];latencies=[]
def measure(i):
 start=time.perf_counter();ok('GET',paths[i%2],a);return (time.perf_counter()-start)*1000
start=time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:latencies=list(pool.map(measure,range(100)))
elapsed=time.perf_counter()-start;latencies.sort()
report={'hardware':platform.platform(),'architecture':platform.machine(),'dockerResources':subprocess.check_output(['docker','info','--format','{{.NCPU}} CPUs / {{.MemTotal}} bytes'],text=True).strip(),'dataset':'3 actor profiles, A-B-C path,2 companies with unique industry; other retained regression data present','replicas':1,'concurrency':2,'requests':100,'durationSeconds':elapsed,'requestsPerSecond':100/elapsed,'p50Ms':latencies[49],'p95Ms':latencies[94],'p99Ms':latencies[98],'errors':0,'queryLimits':{'memberEdges':5000,'returnedSuggestions':20},'limitations':'Small local warm fixture, gateway latency includes auth/network; not capacity or saturation evidence. No cache added.'}
Path(os.getenv('MVP4_DISCOVERY_EVIDENCE','.local/mvp4-discovery-last.json')).write_text(json.dumps(report,indent=2)+'\n')
print('PASS deterministic suggestions, follow exclusions, actual reasons, followed-company job filtering; measured100 requests',json.dumps(report))

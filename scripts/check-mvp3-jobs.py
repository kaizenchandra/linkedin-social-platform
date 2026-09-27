#!/usr/bin/env python3
from hiring_http import *
import statistics,platform
profiles();owner=users['owner'];recruiter=users['recruiter'];outsider=users['outsider'];c=company(owner);cid=c['id'];invite(owner,cid,recruiter)
body={'title':'Principal engineer','description':'Literal 100%_! Oracle backend role','location':'London','workArrangement':'REMOTE','employmentType':'FULL_TIME','salaryMinimum':100,'salaryMaximum':200,'salaryCurrency':'USD','payPeriod':'HOUR'}
assert call('POST','/api/v1/companies/'+cid+'/jobs',outsider,body)[0]==404
assert call('POST','/api/v1/companies/'+cid+'/jobs',owner,{**body,'salaryMaximum':50})[0]==400
assert call('POST','/api/v1/companies/'+cid+'/jobs',owner,{**body,'deadline':'2000-01-01T00:00:00Z'})[0]==400
jobs=[]
for i in range(3):
 j=ok('POST','/api/v1/companies/'+cid+'/jobs',recruiter,{**body,'title':body['title']+str(i)})
 assert call('GET','/api/v1/jobs/'+j['id'],outsider)[0]==404
 assert call('GET','/api/v1/companies/'+cid+'/jobs/'+j['id'],outsider)[0]==404
 j=ok('PUT','/api/v1/jobs/'+j['id'],owner,{**body,'expectedVersion':j['version']})
 assert call('PUT','/api/v1/jobs/'+j['id'],recruiter,{**body,'expectedVersion':0})[0]==409
 j=ok('POST','/api/v1/jobs/'+j['id']+'/publish',recruiter);assert ok('POST','/api/v1/jobs/'+j['id']+'/publish',recruiter)==j;jobs.append(j)
q='/api/v1/jobs?companyId='+cid+'&q=100%25_!%20Oracle&location=lon&workArrangement=REMOTE&employmentType=FULL_TIME&size=2'
first=ok('GET',q,outsider);assert len(first['items'])==2 and first['hasMore'];second=ok('GET',q+'&cursor='+first['nextCursor'],outsider);assert len(second['items'])==1 and not second['hasMore'];assert {j['id'] for j in first['items']+second['items']}=={j['id'] for j in jobs}
closed=ok('POST','/api/v1/jobs/'+jobs[0]['id']+'/close',owner);assert ok('POST','/api/v1/jobs/'+jobs[0]['id']+'/close',owner)==closed
assert call('POST','/api/v1/jobs/'+jobs[0]['id']+'/publish',owner)[0]==409
assert len(ok('GET','/api/v1/jobs?companyId='+cid,outsider)['items'])==2
samples=[]
for _ in range(30):
 start=time.perf_counter();ok('GET',q,outsider);samples.append((time.perf_counter()-start)*1000)
Path('docs/mvp3-search-baseline.json').write_text(json.dumps({'dataset':'one isolated company, three jobs (two published, one closed)','requests':30,'concurrency':1,'durationSeconds':sum(samples)/1000,'requestsPerSecond':30000/sum(samples),'p95Ms':sorted(samples)[28],'host':platform.platform(),'purpose':'Small phase2 functional timing baseline; not a capacity estimate'},indent=2)+'\n')
session.update(companyId=cid,jobId=jobs[1]['id']);save()
print('PASS: current company role enforcement, draft/closed privacy, salary/deadline validation, optimistic edits, idempotent publish/close, literal filters and deterministic cursors; search timing recorded')

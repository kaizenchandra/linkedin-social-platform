#!/usr/bin/env python3
from hiring_http import *
import concurrent.futures,threading
profiles();owner,recruiter,a,b,outsider=[users[n] for n in ['owner','recruiter','alice','bob','outsider']]
c=company(owner);cid=c['id'];company(outsider);invite(owner,cid,recruiter);j=job(recruiter,cid)
body={'jobId':j['id'],'idempotencyKey':str(uuid.uuid4()),'coverNote':'Private application cover marker'}
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 barrier=threading.Barrier(2)
 def submit(_):barrier.wait();return ok('POST','/api/v1/applications',a,body)
 results=list(pool.map(submit,range(2)))
app=results[0];aid=app['id'];path='/api/v1/applications/'+aid;assert results[1]==app
assert ok('POST','/api/v1/applications',a,body)==app
assert call('POST','/api/v1/applications',a,{**body,'coverNote':'Different'})[0]==409
assert call('POST','/api/v1/applications',a,{**body,'idempotencyKey':str(uuid.uuid4())})[0]==409
assert call('POST','/api/v1/applications',owner,{**body,'idempotencyKey':str(uuid.uuid4())})[0]==409
assert call('POST','/api/v1/applications',b,{**body,'idempotencyKey':str(uuid.uuid4()),'applicantId':a['id']})[0]==400
for user in [b,outsider,users['moderator']]:
 assert call('GET',path,user)[0]==404
 assert call('GET',path+'/history',user)[0]==404
 assert call('GET','/api/v1/companies/'+cid+'/applications',user)[0]==404
snapshot=app['profileSnapshot'];updated=ok('PUT','/api/v1/members/me',a,{'displayName':'Updated applicant','headline':'New headline','summary':'Changed after applying','location':'Elsewhere','experiences':[]});assert updated['version']>snapshot['version']
ok('PUT','/api/v1/jobs/'+j['id'],owner,{'title':'Changed after application','description':'Changed posting','location':'Elsewhere','workArrangement':'ONSITE','employmentType':'CONTRACT','expectedVersion':j['version']})
assert ok('GET',path,recruiter)['profileSnapshot']==snapshot
assert ok('GET',path,a)['jobSnapshot']==app['jobSnapshot']
# Personal blocking does not revoke company review access or grant messaging rights.
rel=ok('POST','/api/v1/connections',recruiter,{'targetId':a['id']});ok('POST','/api/v1/connections/'+rel['id']+'/accept',a)
conversation=ok('POST','/api/v1/conversations',a,{'memberId':recruiter['id']})
ok('PUT','/api/v1/blocks/'+recruiter['id'],a,expected=204)
assert call('GET','/api/v1/members/'+a['id'],recruiter)[0]==404
assert ok('GET',path,recruiter)['id']==aid
assert call('POST','/api/v1/conversations/'+conversation['id']+'/messages',recruiter,{'clientMessageId':str(uuid.uuid4()),'body':'Company membership must not bypass block'})[0]==403
review=ok('PUT',path+'/status',recruiter,{'status':'IN_REVIEW','expectedVersion':app['version']})
shortlist=ok('PUT',path+'/status',owner,{'status':'SHORTLISTED','expectedVersion':review['version']})
assert ok('PUT',path+'/status',owner,{'status':'SHORTLISTED','expectedVersion':0})==shortlist
assert call('PUT',path+'/status',a,{'status':'REJECTED','expectedVersion':shortlist['version']})[0]==404
withdrawn=ok('POST',path+'/withdraw',a);assert withdrawn['coverNote']==body['coverNote'];assert withdrawn['profileSnapshot']==snapshot
redacted=ok('GET',path,recruiter);assert redacted['coverNote'] is None and redacted['profileSnapshot'] is None
assert len(ok('GET',path+'/history',a))==4
assert call('PUT',path+'/status',owner,{'status':'REJECTED','expectedVersion':withdrawn['version']})[0]==409
summary=ok('GET','/api/v1/companies/'+cid+'/applications?status=WITHDRAWN',recruiter);assert len(summary['items'])==1 and 'coverNote' not in summary['items'][0]
second=job(owner,cid,'Close race')
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 barrier=threading.Barrier(2)
 def close():barrier.wait();return ok('POST','/api/v1/jobs/'+second['id']+'/close',owner)
 def apply():barrier.wait();return call('POST','/api/v1/applications',b,{'jobId':second['id'],'idempotencyKey':str(uuid.uuid4())})
 closed=pool.submit(close);submitted=pool.submit(apply);assert closed.result()['state']=='CLOSED';result=submitted.result();assert result[0] in [200,409],result
 assert len(ok('GET','/api/v1/applications?jobId='+second['id'],b)['items'])==(1 if result[0]==200 else 0)
ok('DELETE','/api/v1/companies/'+cid+'/members/'+recruiter['id'],owner,expected=204)
assert call('GET',path,recruiter)[0]==404
session.update(companyId=cid,jobId=j['id'],applicationId=aid,applicationInput=body);save()
print('PASS: concurrent idempotent applications, immutable real profile/job snapshots, company/applicant isolation, personal blocking boundary, forward status history, withdrawal redaction, close race and recruiter revocation')

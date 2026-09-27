#!/usr/bin/env python3
from mvp4_support import *
import subprocess
profiles();owner=users['owner'];a=users['alice'];b=users['bob'];mod=users['moderator']
# Isolate searches and ensure previous test preference does not mask explicit consent.
for user in [a,b]:
 for s in ok('GET','/api/v1/jobs/searches',user):ok('DELETE','/api/v1/jobs/searches/'+s['id'],user,expected=204)
 ok('PUT','/api/v1/notifications/preferences/job-alerts',user,{'enabled':True},expected=204)
 assert not ok('GET','/api/v1/notifications/preferences/job-alerts',user)['enabled']
c=company(owner)
body={'name':'Explicit engineer search','keywords':'engineer','companyIds':[c['id']],'location':'London','workArrangement':'REMOTE','employmentType':'FULL_TIME','alertsEnabled':True}
one=ok('POST','/api/v1/jobs/searches',a,body);two=ok('POST','/api/v1/jobs/searches',a,body)
assert ok('GET','/api/v1/notifications/preferences/job-alerts',a)['enabled']
assert not ok('GET','/api/v1/jobs/searches',b)
ok('PUT','/api/v1/jobs/searches/'+one['id'],b,{**body,'expectedVersion':one['version']},expected=404)
j=job(owner,c['id']);wait(lambda:count(a,j['id'])==1,'one overlapping-search alert')
stream=events();publication=next(e for e in stream if e['aggregateId']==j['id'] and e['eventType']=='hiring.job.published');alert=next(e for e in stream if e['aggregateId']==j['id'] and e['eventType']=='hiring.job.alert')
publish_event(publication);publish_event(alert)
# Same-partition fresh envelope is a semantic dedup barrier as well as an event replay.
barrier={**alert,'eventId':str(uuid.uuid4())};publish_event(barrier)
edit={k:j[k] for k in ['title','description','location','workArrangement','employmentType']};edit.update(title='Edited published title',expectedVersion=j['version']);ok('PUT','/api/v1/jobs/'+j['id'],owner,edit)
# A subsequent matching publication proves worker and notification consumer continue to progress.
j2=job(owner,c['id']);wait(lambda:count(a,j2['id'])==1,'post-replay progress');assert count(a,j['id'])==1
# Global disable is independent of saved search consent; queued delivery is suppressed.
ok('PUT','/api/v1/notifications/preferences/job-alerts',a,{'enabled':False},expected=204)
j3=job(owner,c['id'])
# B supplies an observable barrier for the same job with its own explicit consent.
sb=ok('POST','/api/v1/jobs/searches',b,body);j4=job(owner,c['id']);wait(lambda:count(b,j4['id'])==1,'global preference delivery barrier');assert count(a,j4['id'])==0
ok('PUT','/api/v1/notifications/preferences/job-alerts',a,{'enabled':True},expected=204)
# Criteria change is prospective and cannot use a publication created before the change.
compose('stop','notification-service')
try:
 hidden=job(owner,c['id'])
 report=ok('POST','/api/v1/hiring/reports',b,{'jobId':hidden['id'],'reason':'SPAM'})
 ok('POST','/api/v1/hiring/moderation/reports/'+report['id']+'/actions',mod,{'action':'HIDE','reason':'Alert visibility verification'})
 # Disable both searches before queued delivery. Exact matching commit may precede this command.
 for s in [one,two]:ok('PUT','/api/v1/jobs/searches/'+s['id'],a,{**body,'alertsEnabled':False,'expectedVersion':s['version']})
finally:compose('start','--wait','--wait-timeout','150','notification-service')
final=job(owner,c['id']);wait(lambda:count(b,final['id'])==1,'consumer resumed');assert count(a,final['id'])==0
assert count(a,hidden['id'])==0 and count(b,hidden['id'])==0
assert call('GET','/api/v1/jobs/'+hidden['id'],a)[0]==404
Path('.local/mvp4-events.json').write_text(json.dumps([publication,alert,barrier]))
Path('.local/mvp4-alert-fixture.json').write_text(json.dumps({'companyId':c['id'],'jobId':j['id'],'searchId':sb['id'],'recipientId':b['id']}))
print('PASS real Kafka publication/match/notification, overlapping-search dedup, event and semantic replay, edits, explicit consent, preference disable, queued hidden-job suppression, consumer restart')

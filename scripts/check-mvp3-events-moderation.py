#!/usr/bin/env python3
"""Real gateway/Oracle/Kafka hiring notifications and moderation acceptance."""
from hiring_http import *
profiles();o,r,a,b,x=[users[n] for n in ['owner','recruiter','alice','bob','outsider']];m=users['moderator']
c=company(o);logo=upload(o);ok('POST','/api/v1/media/attachments',o,attachment(o,c['id'],logo['id']));i=invite(o,c['id'],r);j=job(r,c['id'])
def notes(u,resource,kind):return [n for n in ok('GET','/api/v1/notifications?size=100',u) if n['resourceId']==resource and n['eventType']==kind]
def wait(test):
 end=time.monotonic()+60
 while time.monotonic()<end:
  value=test()
  if value:return value
  time.sleep(.25)
 raise AssertionError('Notification condition timed out')
wait(lambda:len(notes(r,i['id'],'hiring.invitation.created'))==1)
body={'jobId':j['id'],'idempotencyKey':str(uuid.uuid4()),'coverNote':'Private applicant content'}
app=ok('POST','/api/v1/applications',a,body)
for u in [o,r]:wait(lambda:len(notes(u,app['id'],'hiring.application.submitted'))==1)
app=ok('PUT','/api/v1/applications/'+app['id']+'/status',r,{'status':'IN_REVIEW','expectedVersion':app['version']})
wait(lambda:len(notes(a,app['id'],'hiring.application.status'))==1)
ok('PUT','/api/v1/applications/'+app['id']+'/status',r,{'status':'IN_REVIEW','expectedVersion':0})
report=ok('POST','/api/v1/hiring/reports',b,{'jobId':j['id'],'reason':'SUSPECTED_FRAUD'})
assert report==ok('POST','/api/v1/hiring/reports',b,{'jobId':j['id'],'reason':'SPAM'})
assert ok('GET','/api/v1/hiring/reports',o)['items']==[]
for u in [o,r,a,b,x]:ok('GET','/api/v1/hiring/moderation/reports',u,expected=403)
inspection=ok('POST','/api/v1/hiring/moderation/reports/'+report['id']+'/inspect',m,{'reason':'Investigate submitted report'})
assert inspection['job']['id']==j['id'] and 'reporterId' not in inspection['report']
path='/api/v1/hiring/moderation/reports/'+report['id']+'/actions'
ok('POST',path,m,{'action':'HIDE','reason':'Pending investigation'})
ok('GET','/api/v1/jobs/'+j['id'],a,expected=404)
assert not ok('GET','/api/v1/jobs?companyId='+c['id'],a)['items']
ok('POST','/api/v1/applications',b,{'jobId':j['id'],'idempotencyKey':str(uuid.uuid4())},expected=409)
assert ok('GET','/api/v1/applications/'+app['id'],a)['jobSnapshot']['title']==j['title']
assert ok('GET','/api/v1/applications/'+app['id'],r)['profileSnapshot']
ok('POST',path,m,{'action':'RESTORE','reason':'Investigation resolved'})
assert ok('GET','/api/v1/jobs/'+j['id'],a)['state']=='PUBLISHED'
ok('POST','/api/v1/jobs/'+j['id']+'/close',r)
ok('POST',path,m,{'action':'HIDE','reason':'Further review'})
ok('POST',path,m,{'action':'RESTORE','reason':'Resolved without reopening'})
ok('GET','/api/v1/jobs/'+j['id'],a,expected=404)
assert ok('GET','/api/v1/companies/'+c['id']+'/jobs/'+j['id'],r)['state']=='CLOSED'
assert len(ok('GET','/api/v1/hiring/moderation/reports/'+report['id']+'/audit',m))==5
ok('DELETE','/api/v1/companies/'+c['id']+'/members/'+r['id'],o,expected=204)
ok('GET','/api/v1/applications/'+app['id'],r,expected=404)
assert len(notes(r,app['id'],'hiring.application.submitted'))==1
assert set(notes(r,app['id'],'hiring.application.submitted')[0])=={'id','eventType','actorId','resourceId','occurredAt','readAt'}
session.update(companyId=c['id'],jobId=j['id'],applicationId=app['id'],applicationInput=body,reportId=report['id'],invitationId=i['id'],logoId=logo['id']);save()
print('PASS: real Kafka hiring notifications, private report queue, audited moderator-only actions, hidden submission denial, lifecycle-preserving restore, safe retained notification IDs and revoked recruiter access')

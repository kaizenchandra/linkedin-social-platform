#!/usr/bin/env python3
"""Disruptive verification only for the dedicated local Compose stack; restores dependencies."""
from hiring_http import *
import subprocess
from concurrent.futures import ThreadPoolExecutor
profiles();o,r,a,b=[users[n] for n in ['owner','recruiter','alice','bob']];c=company(o);invite(o,c['id'],r)
def compose(*args):return subprocess.run(['docker','compose',*args],check=True,capture_output=True,text=True).stdout
def wait(test,label,seconds=90):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if test():return
  time.sleep(.3)
 raise AssertionError(label)
def count(u,resource):return len([n for n in ok('GET','/api/v1/notifications?size=100',u) if n['resourceId']==resource])
def apply(j,u=a):return ok('POST','/api/v1/applications',u,{'jobId':j['id'],'idempotencyKey':str(uuid.uuid4()),'coverNote':'Recovery fixture'})
def publish(e):
 subprocess.run(['docker','compose','exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:19092','--producer.config','/tmp/admin.properties','--topic','network.events.v1','--property','parse.key=true'],input=e['aggregateId']+'\t'+json.dumps(e)+'\n',text=True,capture_output=True,check=True,timeout=30)
def events(topic):
 result=subprocess.run(['docker','compose','exec','-T','kafka','/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:19092','--consumer.config','/tmp/admin.properties','--topic',topic,'--from-beginning','--timeout-ms','5000'],capture_output=True,text=True,timeout=25)
 return [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
evidence={};j=job(o,c['id'])
compose('stop','member-service')
try:
 ok('POST','/api/v1/applications',a,{'jobId':j['id'],'idempotencyKey':str(uuid.uuid4())},expected=503)
 assert not ok('GET','/api/v1/applications?jobId='+j['id'],a)['items'];evidence['profileOutage']='503, no application'
finally:compose('start','--wait','--wait-timeout','150','member-service')
compose('stop','kafka')
try:
 start=time.monotonic();app=apply(j);elapsed=time.monotonic()-start;assert elapsed<5
 assert ok('GET','/api/v1/applications/'+app['id'],a)['id']==app['id'];evidence['brokerOutageCommitSeconds']=round(elapsed,3)
finally:compose('start','--wait','--wait-timeout','150','kafka')
wait(lambda:count(o,app['id'])==1 and count(r,app['id'])==1,'outbox recovery')
event=next(e for e in events('network.events.v1') if e['aggregateId']==app['id'] and e['eventType']=='hiring.application.submitted')
publish(event)
# Same-key barrier: replay first, then a new envelope to the same current recipients.
barrier={**event,'eventId':str(uuid.uuid4())};publish(barrier)
wait(lambda:count(o,app['id'])==2 and count(r,app['id'])==2,'dedup barrier')
evidence['replay']='duplicate has no extra effect; same-partition barrier consumed'
compose('stop','notification-service')
try:
 second=apply(job(o,c['id']),b)
 ok('DELETE','/api/v1/companies/'+c['id']+'/members/'+r['id'],o,expected=204)
finally:compose('start','--wait','--wait-timeout','150','notification-service')
wait(lambda:count(o,second['id'])==1,'consumer restart')
assert count(r,second['id'])==0;evidence['currentRecipients']='removed recruiter excluded when queued event consumed'
# An actual recipient lookup outage must exhaust retries into DLT, without claiming dedup.
failed={**event,'eventId':str(uuid.uuid4())}
compose('stop','hiring-service')
try:
 publish(failed)
 wait(lambda:any(e['eventId']==failed['eventId'] for e in events('network.events.v1.DLT')),'recipient failure DLT')
 assert count(o,app['id'])==2
finally:compose('start','--wait','--wait-timeout','150','hiring-service')
publish(failed);wait(lambda:count(o,app['id'])==3,'same-ID repaired replay')
assert count(r,app['id'])==2;evidence['recipientOutage']='DLT after bounded retries; same-eventId replay resolves current owner only'
# Two real relay processes share the same outbox and Oracle lock protocol.
name='professional-network-mvp-hiring-relay-check'
compose('run','-d','--no-deps','--name',name,'hiring-service')
try:
 wait(lambda:subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}',name],text=True).strip()=='healthy','second relay')
 fixtures=[job(o,c['id']) for _ in range(8)]
 with ThreadPoolExecutor(max_workers=4) as pool:apps=list(pool.map(apply,fixtures))
 wait(lambda:all(count(o,v['id'])==1 for v in apps),'two relay delivery')
 evidence['twoRelays']='8 committed applications delivered once per current recipient'
finally:subprocess.run(['docker','rm','-f',name],check=True,capture_output=True)
Path('.local/hiring-replay-event.json').write_text(json.dumps(event))
Path('docs/mvp3-recovery-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('PASS: profile outage, broker outage, real Kafka replay, current recipient resolution, consumer restart, lookup DLT/repaired replay and concurrent outbox relays')

#!/usr/bin/env python3
from mvp5_support import *
from contextlib import ExitStack

a,b,cid=pair();path='/api/v1/conversations/'+cid;evidence={}
def sync(kind,user=a,base='http://localhost:8080'):return ok_at(base,'GET','/api/v1/'+kind+'/sync',user)
def send(base='http://localhost:8080'):return ok_at(base,'POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Durable failure fixture'})
with peers() as peer:
 edge=peer['api-gateway']['base']
 with ExitStack() as stack:
  m0=sync('conversations')['cursor'];n0=sync('notifications')['cursor']
  first=stack.enter_context(Stream(a,'/api/v1/conversations/stream',m0))
  second=stack.enter_context(Stream(a,'/api/v1/conversations/stream',m0,edge))
  nfirst=stack.enter_context(Stream(a,'/api/v1/notifications/stream',n0))
  nsecond=stack.enter_context(Stream(a,'/api/v1/notifications/stream',n0,edge))
  message=send();e1=first.next('message.created',cid);e2=second.next('message.created',cid);assert e1['eventId']==e2['eventId']
  domain=event_for(cid,message['sequence']);wait(lambda:delivered(domain),'notification persisted with two consumers')
  nid=sql("SELECT id FROM notification_app.notifications WHERE event_id='"+domain['eventId']+"';")
  assert nfirst.next('notification.created',nid)['eventId']==nsecond.next('notification.created',nid)['eventId']
  evidence['twoInstances']='Both messaging and notification instances replay the same committed event to their connected device, through separate local gateways'
  cursor=first.cursor;compose('stop','messaging-service')
  try:
   assert first.closed.wait(8),'Messaging shutdown did not close stream'
   missed=send(edge)
   with Stream(a,'/api/v1/conversations/stream',cursor,edge) as resumed:assert resumed.next('message.created',cid)['resourceVersion']==missed['sequence']
  finally:compose('start','--wait','--wait-timeout','150','messaging-service')
  evidence['messagingRestart']='Bounded stream close; another instance serves commands and replays missed state'
 refresh()
 with Stream(a,'/api/v1/notifications/stream',sync('notifications')['cursor']) as notes:
  cursor=notes.cursor;compose('stop','notification-service')
  try:
   assert notes.closed.wait(8),'Notification shutdown did not close stream'
   message=send(edge);domain=event_for(cid,message['sequence']);wait(lambda:delivered(domain),'remaining notification consumer')
   nid=sql("SELECT id FROM notification_app.notifications WHERE event_id='"+domain['eventId']+"';")
   with Stream(a,'/api/v1/notifications/stream',cursor,edge) as resumed:resumed.next('notification.created',nid)
  finally:compose('start','--wait','--wait-timeout','150','notification-service')
  evidence['notificationRestart']='Remaining consumer persists once; peer stream recovers from original cursor'
 refresh()
 with Stream(a,'/api/v1/conversations/stream',sync('conversations')['cursor']) as device:
  cursor=device.cursor;compose('stop','api-gateway')
  try:
   assert device.closed.wait(30),'Gateway shutdown did not close stream'
   missed=send(edge)
  finally:compose('start','--wait','--wait-timeout','150','api-gateway')
  with Stream(a,'/api/v1/conversations/stream',cursor) as resumed:assert resumed.next('message.created',cid)['resourceVersion']==missed['sequence']
  evidence['gatewayRestart']='Gateway closes streams; REST through peer commits; original URL replays after restart'
 refresh()
 with Stream(a,'/api/v1/conversations/stream',sync('conversations')['cursor']) as device:
  cursor=device.cursor;committed=send();compose('pause','oracle')
  try:
   assert device.closed.wait(10),'Stalled DB poll left stream open'
   command="exec 3<>/dev/tcp/localhost/9090; printf 'GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n' >&3; cat <&3"
   readiness=subprocess.run(['docker','exec',container('messaging-service'),'bash','-c',command],capture_output=True,text=True,timeout=12)
   assert '503' in readiness.stdout and 'DOWN' in readiness.stdout,readiness.stdout
  finally:compose('unpause','oracle')
  wait(lambda:call('GET',path,a)[0]==200,'Oracle recovery')
  with Stream(a,'/api/v1/conversations/stream',cursor) as resumed:assert resumed.next('message.created',cid)['resourceVersion']==committed['sequence']
  evidence['databaseInterruption']='Paused real Oracle closes stalled stream; readiness DOWN/503; committed history replays after unpause'
 refresh()
 with Stream(a,'/api/v1/conversations/stream',sync('conversations')['cursor']) as device:
  compose('stop','kafka')
  try:
   start=time.monotonic();message=send();commit=time.monotonic()-start;assert commit<5
   assert device.next('message.created',cid)['resourceVersion']==message['sequence']
  finally:compose('start','--wait','--wait-timeout','150','kafka')
  domain=event_for(cid,message['sequence']);wait(lambda:delivered(domain),'broker recovery notification')
  evidence['brokerOutage']={'businessCommitSeconds':commit,'liveMessageIndependentOfBroker':True,'notificationRecovered':True}
Path('docs/mvp5-failure-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('PASS real two-instance streams, service/gateway restart, Oracle interruption/readiness and Kafka recovery',json.dumps(evidence))

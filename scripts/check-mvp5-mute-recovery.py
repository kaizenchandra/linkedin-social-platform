#!/usr/bin/env python3
from mvp5_support import *
a,b,cid=pair();path='/api/v1/conversations/'+cid
message=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Private mute authority recovery fixture'})
original=event_for(cid,message['sequence']);wait(lambda:delivered(original),'original notification')
failed={**original,'eventId':str(uuid.uuid4())}
compose('stop','messaging-service')
try:
 publish_event(failed)
 wait(lambda:any(e['eventId']==failed['eventId'] for e in events('network.events.v1.DLT')),'mute authority failure reaches DLT')
 assert sql("SELECT COUNT(*) FROM notification_app.consumed_events WHERE event_id='"+failed['eventId']+"';")=='0'
 assert sql("SELECT COUNT(*) FROM notification_app.notifications WHERE event_id='"+failed['eventId']+"';")=='0'
finally:compose('start','--wait','--wait-timeout','150','messaging-service')
refresh();boundary=ok('GET','/api/v1/notifications/sync',a)['cursor']
with Stream(a,'/api/v1/notifications/stream',boundary) as stream:
 publish_event(failed);wait(lambda:delivered(failed),'same-ID recovery')
 nid=sql("SELECT id FROM notification_app.notifications WHERE event_id='"+failed['eventId']+"';")
 stream.next('notification.created',nid)
 publish_event(failed)
 # A following event on the same aggregate partition proves replay has been consumed.
 barrier={**original,'eventId':str(uuid.uuid4())};publish_event(barrier);wait(lambda:delivered(barrier),'replay barrier')
 assert delivered(failed)
 assert sql("SELECT COUNT(*) FROM notification_app.stream_events WHERE resource_id='"+nid+"';")=='1'
Path('docs/mvp5-mute-recovery-evidence.json').write_text(json.dumps({'authorityOutage':'bounded retries and DLT; no notification or committed dedup','repairedReplay':'same event ID commits notification and replay history once','duplicateReplay':'positive partition barrier; one durable stream event'},indent=2)+'\n')
print('PASS authoritative mute outage fails closed, bounded DLT, repaired replay and stream deduplication')

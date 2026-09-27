#!/usr/bin/env python3
from mvp4_support import *
from sse_client import Stream
refresh();profiles();a=users['alice'];b=users['bob'];c=users['owner']
for u,v in [(a,b),(b,a)]:ok('DELETE','/api/v1/blocks/'+v['id'],u,expected=204)
connection=ok('POST','/api/v1/connections',a,{'targetId':b['id']});ok('POST','/api/v1/connections/'+connection['id']+'/accept',b)
conversation=ok('POST','/api/v1/conversations',a,{'memberId':b['id']});cid=conversation['id'];path='/api/v1/conversations/'+cid
sa=ok('GET','/api/v1/conversations/sync',a);sb=ok('GET','/api/v1/conversations/sync',b)
with Stream(a,'/api/v1/conversations/stream',sa['cursor']) as one,Stream(a,'/api/v1/conversations/stream',sa['cursor']) as two,Stream(b,'/api/v1/conversations/stream',sb['cursor']) as other:
 start=time.monotonic();message=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Private SSE fixture, never in invalidations'})
 first=one.next('message.created',cid);second=two.next('message.created',cid);other.next('message.created',cid)
 assert first['eventId']==second['eventId'];assert 'Private SSE' not in json.dumps(first) and 'body' not in first
 assert time.monotonic()-start<5,'Stream did not flush promptly'
 assert ok('GET',path,a)['unreadCount']==1
 read=ok('PUT',path+'/read',a,{'messageId':message['id']});assert read['unreadCount']==0
 for stream in [one,two]:assert stream.next('conversation.read',cid)['resourceVersion']==read['readVersion']
 last=two.cursor;two.close()
 missed=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Disconnected device fixture'})
 one.next('message.created',cid)
 with Stream(a,'/api/v1/conversations/stream',last) as resumed:
  event=resumed.next('message.created',cid);assert event['resourceVersion']==missed['sequence']
  assert ok('GET',path,a)['unreadCount']==1
try:Stream(c,'/api/v1/conversations/stream',sa['cursor']);raise AssertionError('Foreign cursor admitted')
except urllib.error.HTTPError as e:assert e.code==400,e.code
assert call('GET',path,c)[0]==404
session['mvp5ConversationId']=cid;session['mvp5MessageId']=missed['id'];save()
print('PASS real gateway SSE flush, two-device message/read synchronization, owner isolation, no body payload, disconnect/replay and authoritative unread counts')

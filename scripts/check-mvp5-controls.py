#!/usr/bin/env python3
from mvp5_support import *
from sse_client import Stream
from contextlib import ExitStack
refresh();profiles();a=users['alice'];b=users['bob'];c=users['owner']
for u,v in [(a,b),(b,a)]:ok('DELETE','/api/v1/blocks/'+v['id'],u,expected=204)
connection=ok('POST','/api/v1/connections',a,{'targetId':b['id']});ok('POST','/api/v1/connections/'+connection['id']+'/accept',b)
cid=ok('POST','/api/v1/conversations',a,{'memberId':b['id']})['id'];path='/api/v1/conversations/'+cid
snap=lambda name,u:ok('GET','/api/v1/'+name+'/sync',u)
notes=lambda u:[n for n in ok('GET','/api/v1/notifications?size=100',u) if n['eventType']=='message.sent' and n['resourceId']==cid]
def send(u,text):return ok('POST',path+'/messages',u,{'clientMessageId':str(uuid.uuid4()),'body':text})
with ExitStack() as stack:
 streams={}
 for name,user,label in [('conversations',a,'a1'),('conversations',a,'a2'),('notifications',a,'n1'),('notifications',a,'n2'),('notifications',b,'nb')]:
  streams[label]=stack.enter_context(Stream(user,'/api/v1/'+name+'/stream',snap(name,user)['cursor']))
 first=send(b,'Private message, absent from SSE and notifications')
 for label in ['a1','a2']:streams[label].next('message.created',cid)
 wait(lambda:len(notes(a))==1,'first generic message notification');note=notes(a)[0]
 for label in ['n1','n2']:assert streams[label].next('notification.created',note['id'])['resourceVersion']==0
 ok('PUT','/api/v1/notifications/'+note['id']+'/read',a)
 for label in ['n1','n2']:assert streams[label].next('notification.read',note['id'])['resourceVersion']==1
 assert all('body' not in n for n in notes(a));assert call('PUT','/api/v1/notifications/'+note['id']+'/read',c)[0]==404
 state=ok('PATCH',path+'/preferences',a,{'muted':True});assert state['readPosition']==0
 for label in ['a1','a2']:streams[label].next('conversation.preferences',cid)
 send(b,'Muted message still delivered')
 for label in ['a1','a2']:streams[label].next('message.created',cid)
 # Same conversation Kafka key: the unmuted B notification is a positive consumer barrier.
 send(a,'Barrier for B');wait(lambda:len(notes(b))==1,'mute decision consumed');assert len(notes(a))==1
 assert ok('GET',path,b)['muted'] is False
 archived=ok('PATCH',path+'/preferences',a,{'archived':True});assert archived['muted'] and archived['readPosition']==0
 assert cid not in [x['id'] for x in ok('GET','/api/v1/conversations',a)['items']]
 assert cid in [x['id'] for x in ok('GET','/api/v1/conversations?archived=true',a)['items']]
 send(b,'Incoming unarchives without reading')
 state=ok('GET',path,a);assert not state['archived'] and state['muted'] and state['unreadCount']==3
 send(a,'Second consumer barrier');wait(lambda:len(notes(b))==2,'archived incoming mute consumed');assert len(notes(a))==1
 ok('PATCH',path+'/preferences',a,{'muted':False});send(b,'Unmuted new message');wait(lambda:len(notes(a))==2,'unmute permits only future notification')
 original=next(e for e in events() if e['eventType']=='message.sent' and e['aggregateId']==cid and e['payload']['recipientId']==a['id'])
 publish_event(original);publish_event(original);send(b,'Replay barrier');wait(lambda:len(notes(a))==3,'post-replay barrier');assert len(notes(a))==3
 ok('PUT','/api/v1/blocks/'+b['id'],a,expected=204)
 ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Blocked'},expected=403)
 assert len(ok('GET',path+'/messages?size=100',a)['items'])==7
 assert snap('conversations',a)['unreadCount']==5
 assert snap('notifications',a)['unreadCount']==sum(n['readAt'] is None for n in ok('GET','/api/v1/notifications?size=100',a))
 with internal_base('messaging-service',8085) as base:
  request=urllib.request.Request(base+'/internal/v1/messaging/notification-eligibility',json.dumps({'memberId':a['id'],'conversationId':cid}).encode(),{'Authorization':'Bearer '+a['access_token'],'Content-Type':'application/json'})
  try:urllib.request.urlopen(request);raise AssertionError('Member token accepted internally')
  except urllib.error.HTTPError as e:assert e.code==403,e.code
session['mvp5ConversationId']=cid;session['mvp5MessageId']=first['id'];save()
print('PASS two-device notification/read streams, authoritative unread state, owner isolation, mute barriers, archive/unarchive, block/history, scoped preference authority and real Kafka replay deduplication')

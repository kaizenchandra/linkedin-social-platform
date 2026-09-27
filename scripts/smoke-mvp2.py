#!/usr/bin/env python3
"""Complete release journey. Run auth-test-setup and auth-moderator-setup first."""
exec(open('scripts/check-mvp2-media.py').read().split("assert upload(a,b'invalid bytes')")[0])
mod=session['moderator'];mt=mod['access_token']
status,avatar=upload(a,png());assert status==200,(status,avatar)
assert attach(a,'PROFILE',a['id'],[avatar['id']])[0]==200
status,post=call('POST','/api/v1/posts',a['access_token'],{'body':'MVP-2 private post','visibility':'CONNECTIONS'});assert status==200
pid=post['id'];path='/api/v1/posts/'+pid
status,image=upload(a,png());assert status==200
assert attach(a,'POST',pid,[image['id']])[0]==200
assert call('GET',path,b['access_token'])[0]==200
assert call('GET',path,c['access_token'])[0]==404
assert download(b,image['id'])[0]==200
assert download(c,image['id'])[0]==404
assert call('PUT',path+'/like',b['access_token'])[0]==204
status,comment=call('POST',path+'/comments',b['access_token'],{'body':'Connected comment'});assert status==200
status,conv=call('POST','/api/v1/conversations',a['access_token'],{'memberId':b['id']});assert status==200
cid=conv['id'];cp='/api/v1/conversations/'+cid
body={'clientMessageId':str(uuid.uuid4()),'body':'MVP-2 private message'}
status,message=call('POST',cp+'/messages',a['access_token'],body);assert status==200
assert call('POST',cp+'/messages',a['access_token'],body)[1]['id']==message['id']
assert len(call('GET',cp+'/messages',b['access_token'])[1]['items'])==1
assert call('PUT',cp+'/read',b['access_token'],{'messageId':message['id']})[1]['unreadCount']==0
assert call('GET',cp+'/messages',c['access_token'])[0]==404
assert call('GET',cp+'/messages',mt)[0]==404
assert call('PUT','/api/v1/blocks/'+b['id'],a['access_token'])[0]==204
assert call('POST',cp+'/messages',b['access_token'],{'clientMessageId':str(uuid.uuid4()),'body':'Blocked'})[0]==403
assert call('GET',path,b['access_token'])[0]==404
assert download(b,image['id'])[0]==404
assert download(b,avatar['id'])[0]==404
assert len(call('GET',cp+'/messages',b['access_token'])[1]['items'])==1
assert call('DELETE','/api/v1/blocks/'+b['id'],a['access_token'])[0]==204
assert call('GET','/api/v1/connections',a['access_token'])[1]==[]
status,rel=call('POST','/api/v1/connections',a['access_token'],{'targetId':b['id']});assert status==200
assert call('POST','/api/v1/connections/'+rel['id']+'/accept',b['access_token'])[0]==200
status,report=call('POST','/api/v1/reports',b['access_token'],{'targetType':'POST','targetId':pid,'reason':'OTHER','explanation':'Manual review fixture'});assert status==200
rid=report['id'];rp='/api/v1/moderation/reports/'+rid
assert call('GET','/api/v1/moderation/reports',a['access_token'])[0]==403
assert call('POST',rp+'/inspect',mt,{'reason':'Review fixture'})[0]==200
assert call('POST',rp+'/actions',mt,{'action':'HIDE','reason':'Investigating fixture'})[0]==200
for u in [a,b,c]:
 assert call('GET',path,u['access_token'])[0]==404
 assert call('GET',path+'/comments',u['access_token'])[0]==404
 assert download(u,image['id'])[0]==404
assert call('POST',rp+'/actions',mt,{'action':'RESTORE','reason':'Fixture reviewed'})[0]==200
assert call('GET',path,b['access_token'])[0]==200
assert call('GET',path,c['access_token'])[0]==404
assert download(b,image['id'])[0]==200
assert download(c,image['id'])[0]==404
end=time.monotonic()+45
while time.monotonic()<end:
 an=call('GET','/api/v1/notifications?size=100',a['access_token'])[1];bn=call('GET','/api/v1/notifications?size=100',b['access_token'])[1]
 kinds={n['eventType'] for n in an if n['resourceId']==pid}
 if {'post.liked','post.commented','moderation.hidden','moderation.restored'}<=kinds and any(n['eventType']=='message.sent' and n['resourceId']==cid for n in bn):break
 time.sleep(.25)
else:raise AssertionError('Expected release notifications missing')
assert 'MVP-2 private' not in json.dumps(an+bn)
session.update(postId=pid,commentId=comment['id'],mediaId=image['id'],avatarId=avatar['id'],conversationId=cid,messageId=message['id'],reportId=rid,connectionId=rel['id'])
Path('.local/session.json').write_text(json.dumps(session))
print('PASS: complete MVP-2 three-member/moderator journey; images, current visibility, messaging/read state, blocking/reconnection, audited moderation and safe notifications')

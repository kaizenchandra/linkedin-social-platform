#!/usr/bin/env python3
exec(open('scripts/check-mvp2-media.py').read().split("assert upload(a,b'invalid bytes')")[0])
mod=session['moderator'];mt=mod['access_token']
assert call('GET','/api/v1/moderation/reports',a['access_token'])[0]==403
status,post=call('POST','/api/v1/posts',a['access_token'],{'body':'Private reported post','visibility':'CONNECTIONS'});assert status==200
pid=post['id'];path='/api/v1/posts/'+pid
status,m=upload(a,png());assert status==200
assert attach(a,'POST',pid,[m['id']])[0]==200
assert call('POST','/api/v1/reports',c['access_token'],{'targetType':'POST','targetId':pid,'reason':'SPAM'})[0]==404
report={'targetType':'POST','targetId':pid,'reason':'SPAM'}
status,r=call('POST','/api/v1/reports',b['access_token'],report);assert status==200,(status,r)
rid=r['id'];rp='/api/v1/moderation/reports/'+rid
assert call('POST','/api/v1/reports',b['access_token'],report)[1]['id']==rid
assert call('GET','/api/v1/reports',a['access_token'])[1]==[]
assert 'reporterId' not in r
assert call('POST',rp+'/inspect',a['access_token'],{'reason':'forged role','role':'moderator'})[0]==403
status,inspection=call('POST',rp+'/inspect',mt,{'reason':'Investigate reported content'});assert status==200,(status,inspection)
assert inspection['body']=='Private reported post'
status,comment=call('POST',path+'/comments',b['access_token'],{'body':'Reported comment'});assert status==200
cr=call('POST','/api/v1/reports',a['access_token'],{'targetType':'COMMENT','targetId':comment['id'],'reason':'HARASSMENT'})[1]
assert call('POST','/api/v1/moderation/reports/'+cr['id']+'/actions',mt,{'action':'HIDE','reason':'Harassment'})[0]==200
assert call('GET',path+'/comments',a['access_token'])[1]==[]
assert call('GET',path,a['access_token'])[1]['commentCount']==0
for _ in range(2):assert call('POST',rp+'/actions',mt,{'action':'HIDE','reason':'Spam'})[0]==200
for user in [a,b,c]:
 assert call('GET',path,user['access_token'])[0]==404
 assert call('GET',path+'/comments',user['access_token'])[0]==404
 assert call('PUT',path+'/like',user['access_token'])[0]==404
 assert download(user,m['id'])[0]==404
assert pid not in [x['id'] for x in call('GET','/api/v1/feed',b['access_token'])[1]['items']]
assert call('POST',rp+'/actions',mt,{'action':'RESTORE','reason':'Context reviewed'})[0]==200
assert call('GET',path,b['access_token'])[0]==200
assert call('GET',path,c['access_token'])[0]==404
assert download(b,m['id'])[0]==200
assert download(c,m['id'])[0]==404
assert len(call('GET',rp+'/audit',mt)[1])==4
assert call('DELETE',path,a['access_token'])[0]==204
assert call('POST',rp+'/actions',mt,{'action':'RESTORE','reason':'Must not resurrect'})[0]==409
assert call('GET',path,a['access_token'])[0]==404
assert download(a,m['id'])[0]==404
assert call('POST',rp+'/actions',mt,{'action':'DISMISS','reason':'Author removed content'})[0]==200
end=time.monotonic()+45
while time.monotonic()<end:
 notes=[n for n in call('GET','/api/v1/notifications?size=100',a['access_token'])[1] if n['resourceId']==pid and n['eventType'].startswith('moderation.')]
 if len(notes)==2:break
 time.sleep(.25)
else:raise AssertionError(('Missing or duplicate moderation notifications',notes))
assert all('body' not in n and 'Private reported' not in json.dumps(n) for n in notes)
print('PASS: trusted moderator role; private reports and audited inspection; hidden post/comment/media/count restrictions; restore preserves policy; author deletion cannot be resurrected; generic deduplicated notifications')

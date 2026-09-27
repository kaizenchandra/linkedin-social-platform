#!/usr/bin/env python3
"""Real gateway policy checks; creates isolated users through auth-test-setup first."""
exec(open('scripts/check-connections.py').read())
status,post=call('POST','/api/v1/posts',a['access_token'],{'body':'Connections only','visibility':'CONNECTIONS'});assert status==200,(status,post)
pid=post['id'];path='/api/v1/posts/'+pid
assert call('GET',path,b['access_token'])[0]==200
assert call('GET',path,c['access_token'])[0]==404
assert call('GET',path+'/comments',c['access_token'])[0]==404
assert call('POST',path+'/comments',c['access_token'],{'body':'forged'})[0]==404
assert call('PUT',path+'/like',c['access_token'])[0]==404
assert pid not in [x['id'] for x in call('GET','/api/v1/posts?authorId='+a['id'],c['access_token'])[1]['items']]
for _ in range(2):assert call('PUT','/api/v1/blocks/'+b['id'],a['access_token'])[0]==204
for owner,viewer in [(a,b),(b,a)]:
 assert call('GET','/api/v1/members/'+owner['id'],viewer['access_token'])[0]==404
 assert owner['id'] not in [x['id'] for x in call('POST','/api/v1/members/lookup',viewer['access_token'],[owner['id']])[1]]
 assert owner['id'] not in [x['id'] for x in call('GET','/api/v1/members?q='+owner['name'],viewer['access_token'])[1]]
assert call('GET',path,b['access_token'])[0]==404
assert call('GET','/api/v1/connections',a['access_token'])[1]==[]
assert call('POST','/api/v1/connections',b['access_token'],{'targetId':a['id']})[0]==404
assert pid not in [x['id'] for x in call('GET','/api/v1/feed',b['access_token'])[1]['items']]
for _ in range(2):assert call('DELETE','/api/v1/blocks/'+b['id'],a['access_token'])[0]==204
assert call('GET','/api/v1/connections',a['access_token'])[1]==[]
assert call('GET',path,b['access_token'])[0]==404
assert call('PUT',path,a['access_token'],{'body':'Now members','visibility':'MEMBERS'})[0]==200
assert call('GET',path,c['access_token'])[0]==200
assert call('PUT','/api/v1/blocks/'+a['id'],a['access_token'])[0]==400
print('PASS: live current visibility on direct/list/feed/comments/likes; bidirectional blocks; idempotent unblock without reconnection')

#!/usr/bin/env python3
exec(open('scripts/check-connections.py').read())
status,post=call('POST','/api/v1/posts',a['access_token'],{'body':'A verified professional networking post'});assert status==200,(status,post)
pid=post['id'];path='/api/v1/posts/'+pid
assert call('PUT',path,c['access_token'],{'body':'forged'})[0]==404
assert call('DELETE',path,c['access_token'])[0]==404
for _ in range(2):assert call('PUT',path+'/like',b['access_token'])[0]==204
assert call('GET',path,b['access_token'])[1]['likeCount']==1
status,comment=call('POST',path+'/comments',b['access_token'],{'body':'Great work'});assert status==200,(status,comment)
assert call('DELETE','/api/v1/comments/'+comment['id'],c['access_token'])[0]==404
status,feed=call('GET','/api/v1/feed',b['access_token']);assert status==200,(status,feed)
assert pid in [p['id'] for p in feed['items']]
assert call('POST','/api/v1/connections/'+rid+'/remove',a['access_token'])[0]==200
assert pid not in [p['id'] for p in call('GET','/api/v1/feed',b['access_token'])[1]['items']]
# Connections affect feed membership, never authenticated post visibility.
assert call('GET',path,c['access_token'])[0]==200
session['postId']=pid;session['commentId']=comment['id'];Path('.local/session.json').write_text(json.dumps(session))
print('PASS: content ownership, unique repeated likes, comments, connected feed and immediate connection removal')

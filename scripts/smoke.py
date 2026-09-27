#!/usr/bin/env python3
"""Full non-disruptive API journey. Run auth-test-setup.py first for isolated users."""
exec(open('scripts/check-content.py').read())
def wait_for(predicate,description,seconds=60):
 deadline=time.monotonic()+seconds
 while True:
  result=predicate()
  if result:return result
  if time.monotonic()>=deadline:raise AssertionError('Timeout: '+description)
  time.sleep(.25)
def inbox(user):
 status,body=call('GET','/api/v1/notifications?size=100',user['access_token']);assert status==200,(status,body);return body
def post_notifications():return [n for n in inbox(a) if n['resourceId']==pid]
notes=wait_for(lambda:post_notifications() if len(post_notifications())==2 else None,'like/comment notifications')
assert {n['eventType'] for n in notes}=={'post.liked','post.commented'}
assert any(n['eventType']=='connection.accepted' for n in inbox(a))
assert any(n['eventType']=='connection.requested' for n in inbox(b))
assert inbox(c)==[]
nid=notes[0]['id']
assert call('PUT','/api/v1/notifications/'+nid+'/read',c['access_token'])[0]==404
status,read=call('PUT','/api/v1/notifications/'+nid+'/read',a['access_token']);assert status==200
assert call('PUT','/api/v1/notifications/'+nid+'/read',a['access_token'])[1]['readAt']==read['readAt']
assert call('PUT','/api/v1/posts/'+pid+'/like',a['access_token'])[0]==204
assert call('POST','/api/v1/posts/'+pid+'/comments',a['access_token'],{'body':'Own comment, no notification'})[0]==200
session['notificationId']=nid;Path('.local/session.json').write_text(json.dumps(session))
print('PASS: durable connection/like/comment notifications; owner isolation; idempotent read; own actions accepted')

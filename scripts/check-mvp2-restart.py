#!/usr/bin/env python3
import json,sys,subprocess,time,urllib.request,hashlib
from pathlib import Path
s=json.loads(Path('.local/session.json').read_text());a,b=s['users'][:2];backend=sys.argv[1] if len(sys.argv)>1 else 'compose'
def get(path,user,binary=False):
 with urllib.request.urlopen(urllib.request.Request('http://localhost:8080'+path,headers={'Authorization':'Bearer '+user['access_token']}),timeout=8) as r:
  data=r.read();return hashlib.sha256(data).hexdigest() if binary else json.loads(data)
def snapshot():return [get('/api/v1/members/me',a),get('/api/v1/posts/'+s['postId'],b),get('/api/v1/conversations/'+s['conversationId'],b),get('/api/v1/conversations/'+s['conversationId']+'/messages',b)['items'],get('/api/v1/media/'+s['mediaId']+'/content',b,True)]
before=snapshot();services=['member-service','content-service','notification-service','media-service','messaging-service','api-gateway']
if backend=='kind':
 for service in services:
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','restart','deployment/'+service],check=True)
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','status','deployment/'+service,'--timeout=180s'],check=True)
else:
 subprocess.run(['docker','compose','restart',*services],check=True)
 if backend=='database':subprocess.run(['docker','compose','restart','oracle'],check=True)
end=time.monotonic()+150
while time.monotonic()<end:
 try:
  if snapshot()==before:break
 except (OSError,urllib.error.URLError):pass
 time.sleep(.5)
else:raise AssertionError('Persisted release state mismatch after restart')
print('PASS: '+backend+' restart preserves profile/avatar, post/images, exact messages and read position/unread count')

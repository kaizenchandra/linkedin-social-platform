#!/usr/bin/env python3
"""Verify committed state across application restarts; backend is compose or kind."""
import sys,subprocess,json,time,urllib.request,urllib.error
from pathlib import Path
backend=sys.argv[1] if len(sys.argv)>1 else 'compose';s=json.loads(Path('.local/session.json').read_text());token=s['users'][0]['access_token']
def get(path):
 with urllib.request.urlopen(urllib.request.Request('http://localhost:8080'+path,headers={'Authorization':'Bearer '+token}),timeout=5) as r:return json.load(r)
paths=['/api/v1/members/me','/api/v1/posts/'+s['postId'],'/api/v1/notifications?size=100'];before=[get(p) for p in paths]
if backend=='kind':
 for svc in ['member-service','content-service','notification-service','api-gateway']:
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','restart','deployment/'+svc],check=True)
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','status','deployment/'+svc,'--timeout=180s'],check=True)
else:subprocess.run(['docker','compose','restart','member-service','content-service','notification-service','api-gateway'],check=True)
end=time.monotonic()+90
while time.monotonic()<end:
 try:
  after=[get(p) for p in paths]
  if all(a==b for a,b in zip(before,after)):break
 except (OSError,urllib.error.URLError):pass
 time.sleep(.5)
else:raise AssertionError('Persisted state failed to survive restart')
print('PASS: '+backend+' application restart preserves profiles, post/counts, notifications/read state')

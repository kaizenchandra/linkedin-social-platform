#!/usr/bin/env python3
"""Exact persisted message/read/preference and replay-head comparison across app restarts."""
from mvp5_support import *
import sys
backend=sys.argv[1] if len(sys.argv)>1 else 'compose';a,b,cid=pair();path='/api/v1/conversations/'+cid
message=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Private restart fixture'})
wait(lambda:{n['eventType'] for n in ok('GET','/api/v1/notifications?size=100',a)}>={'connection.accepted','message.sent'},'all fixture notification transactions before snapshot')
ok('PUT',path+'/read',a,{'messageId':message['id']});ok('PATCH',path+'/preferences',a,{'muted':True,'archived':True})
def snapshot():return [ok('GET',path,a),ok('GET',path+'/messages',a),ok('GET','/api/v1/notifications/sync',a),ok('GET','/api/v1/conversations/sync',a)]
before=snapshot()
services=['messaging-service','notification-service','api-gateway']
if backend=='kind':
 for service in services:
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','restart','deployment/'+service],check=True,capture_output=True)
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','status','deployment/'+service,'--timeout=180s'],check=True,capture_output=True)
else:
 compose('restart',*services)
reported=False
def differences(left,right,path='state'):
 if type(left)!=type(right):return [path+':type']
 if isinstance(left,dict):return [p for k in left.keys()|right.keys() for p in differences(left.get(k),right.get(k),path+'.'+k)]
 if isinstance(left,list):
  if len(left)!=len(right):return [path+':length']
  return [p for i,(a,b) in enumerate(zip(left,right)) for p in differences(a,b,path+'['+str(i)+']')]
 return [] if left==right else [path]
def same():
 global reported
 try:
  after=snapshot()
  if after!=before and not reported:
   print('State mismatch field paths (no values):',differences(before,after),flush=True);reported=True
  return after==before
 except (OSError,AssertionError):return False
wait(same,'exact durable state after restart',180)
with Stream(a,'/api/v1/conversations/stream',before[3]['cursor']) as stream:
 ok('PATCH',path+'/preferences',a,{'muted':False});stream.next('conversation.preferences',cid)
Path('docs/mvp5-'+backend+'-restart-evidence.json').write_text(json.dumps({'backend':backend,'exactStatePreserved':['messages','read position/version','mute/archive/version','notification unread/count/cursor','messaging sync/cursor'],'subsequentStreamProgress':True},indent=2)+'\n')
print('PASS '+backend+' restart preserves messages, read/preference state and independent replay boundaries')

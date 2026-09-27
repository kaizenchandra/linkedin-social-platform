#!/usr/bin/env python3
"""Disrupt only the dedicated Compose stack, restoring each dependency in finally."""
import json,time,uuid,subprocess,urllib.request,urllib.error
from pathlib import Path
s=json.loads(Path('.local/session.json').read_text());a,b=s['users'][:2];pid=s['postId'];cid=s['conversationId']
def call(method,path,user,data=None):
 try:
  with urllib.request.urlopen(urllib.request.Request('http://localhost:8080'+path,None if data is None else json.dumps(data).encode(),{'Authorization':'Bearer '+user['access_token'],'Content-Type':'application/json'},method=method),timeout=20) as r:
   body=r.read();return r.status,json.loads(body) if body else None
 except urllib.error.HTTPError as e:return e.code,e.read().decode()
def compose(*args):subprocess.run(['docker','compose',*args],check=True,stdout=subprocess.DEVNULL)
def note_count(user,resource):return len([n for n in call('GET','/api/v1/notifications?size=100',user)[1] if n['resourceId']==resource])
def wait_notes(user,resource,expected):
 end=time.monotonic()+90
 while time.monotonic()<end:
  if note_count(user,resource)==expected:return
  time.sleep(.3)
 raise AssertionError('Notification recovery timeout')
evidence={}
compose('stop','member-service')
try:
 for method,path,body in [('GET','/api/v1/posts/'+pid,None),('GET','/api/v1/feed',None),('GET','/api/v1/media/'+s['mediaId']+'/content',None),('POST','/api/v1/conversations/'+cid+'/messages',{'clientMessageId':str(uuid.uuid4()),'body':'Must not commit'})]:
  status,_=call(method,path,b,body);assert status==503,(path,status)
 evidence['memberOutage']='post/feed/media/new-send503'
finally:compose('up','-d','--no-deps','--wait','--wait-timeout','120','member-service')
compose('stop','object-store')
try:
 boundary='test'+uuid.uuid4().hex;data=Path('requests/fixtures/image.png').read_bytes();body=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="test.png"\r\nContent-Type: image/png\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode()
 try:urllib.request.urlopen(urllib.request.Request('http://localhost:8080/api/v1/media',body,{'Authorization':'Bearer '+a['access_token'],'Content-Type':'multipart/form-data; boundary='+boundary}),timeout=20);raise AssertionError('False upload success')
 except urllib.error.HTTPError as e:assert e.code==503,e.code
 evidence['objectStoreOutage']='upload503, no false READY response'
finally:compose('up','-d','--no-deps','--wait','--wait-timeout','120','object-store')
before=note_count(b,cid);compose('stop','kafka')
try:
 started=time.monotonic();status,m=call('POST','/api/v1/conversations/'+cid+'/messages',a,{'clientMessageId':str(uuid.uuid4()),'body':'Durable broker outage fixture'});elapsed=time.monotonic()-started
 assert status==200,(status,m);assert elapsed<5,elapsed
 assert m['id'] in [x['id'] for x in call('GET','/api/v1/conversations/'+cid+'/messages',b)[1]['items']]
 evidence['brokerOutageCommitSeconds']=round(elapsed,3)
finally:compose('up','-d','--no-deps','--wait','--wait-timeout','120','kafka')
wait_notes(b,cid,before+1)
before=note_count(b,cid);compose('stop','notification-service')
try:
 status,_=call('POST','/api/v1/conversations/'+cid+'/messages',a,{'clientMessageId':str(uuid.uuid4()),'body':'Consumer restart fixture'});assert status==200
finally:compose('up','-d','--no-deps','--wait','--wait-timeout','120','notification-service')
wait_notes(b,cid,before+1);evidence['consumerRestart']='committed notification recovered'
Path('docs/mvp2-recovery-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('PASS: authoritative policy outages fail closed; S3 outage rejects upload; Kafka outage preserves committed message/outbox; consumer restart catches up')

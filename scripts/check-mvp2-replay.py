#!/usr/bin/env python3
"""Replay message/moderation envelopes in Compose or the dedicated kind cluster."""
import json,subprocess,time,sys,urllib.request,uuid
from pathlib import Path
s=json.loads(Path('.local/session.json').read_text());a,b=s['users'][:2];pid=s['postId'];cid=s['conversationId'];backend=sys.argv[1] if len(sys.argv)>1 else 'compose'
prefix=['scripts/kubectl-local.sh','-n','network-mvp','exec','-i','deploy/kafka','--'] if backend=='kind' else ['docker','compose','exec','-T','kafka']
def call(method,path,u,data=None):
 with urllib.request.urlopen(urllib.request.Request('http://localhost:8080'+path,None if data is None else json.dumps(data).encode(),{'Authorization':'Bearer '+u['access_token'],'Content-Type':'application/json'},method=method),timeout=10) as r:return json.loads(r.read() or 'null')
def count(u,id):return len([n for n in call('GET','/api/v1/notifications?size=100',u) if n['resourceId']==id])
r=subprocess.run(prefix+['/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:19092','--consumer.config','/tmp/admin.properties','--topic','network.events.v1','--from-beginning','--timeout-ms','8000'],text=True,capture_output=True,timeout=30)
all_events=[json.loads(l) for l in r.stdout.splitlines() if l.startswith('{')]
message=next(e for e in all_events if e['aggregateId']==cid and e['eventType']=='message.sent');moderation=next(e for e in all_events if e['aggregateId']==pid and e['eventType']=='moderation.hidden')
before=(count(a,pid),count(b,cid))
subprocess.run(prefix+['/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:19092','--producer.config','/tmp/admin.properties','--topic','network.events.v1','--property','parse.key=true'],input=''.join(e['aggregateId']+'\t'+json.dumps(e)+'\n' for e in [message,moderation]),text=True,capture_output=True,check=True,timeout=30)
call('POST','/api/v1/posts/'+pid+'/comments',b,{'body':'Replay ordering barrier'})
call('POST','/api/v1/conversations/'+cid+'/messages',a,{'clientMessageId':str(uuid.uuid4()),'body':'Replay ordering barrier'})
end=time.monotonic()+60
while time.monotonic()<end:
 counts=(count(a,pid),count(b,cid))
 if counts==(before[0]+1,before[1]+1):break
 if counts[0]>before[0]+1 or counts[1]>before[1]+1:raise AssertionError(('Duplicate notification',counts,before))
 time.sleep(.3)
else:raise AssertionError(('Replay barriers missing',counts,before))
Path('.local/mvp2-replay-events.json').write_text(json.dumps([message,moderation]))
Path('.local/replay-event.json').write_text(json.dumps(moderation))
print('PASS: '+backend+' duplicate message/moderation events produce no extra notifications, with same-key processing barriers')

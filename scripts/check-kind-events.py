#!/usr/bin/env python3
"""Replay and two-ready-replica checks in the dedicated kind namespace."""
import json,subprocess,time,urllib.request
from pathlib import Path
s=json.loads(Path('.local/session.json').read_text());a,b=s['users'][:2];pid=s['postId'];k=['scripts/kubectl-local.sh','-n','network-mvp']
def request(method,path,user,data=None):
 headers={'Authorization':'Bearer '+user['access_token'],'Content-Type':'application/json'}
 with urllib.request.urlopen(urllib.request.Request('http://localhost:8080'+path,None if data is None else json.dumps(data).encode(),headers,method=method),timeout=10) as r:return json.loads(r.read() or 'null')
def notes():return [n for n in request('GET','/api/v1/notifications?size=100',a) if n['resourceId']==pid]
def wait_count(expected):
 end=time.monotonic()+60
 while time.monotonic()<end:
  if len(notes())==expected:return
  time.sleep(.25)
 raise AssertionError('Unexpected notification count')
r=subprocess.run(k+['exec','deploy/kafka','--','/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:19092','--consumer.config','/tmp/admin.properties','--topic','network.events.v1','--from-beginning','--timeout-ms','8000'],capture_output=True,text=True,timeout=30)
e=next(json.loads(l) for l in r.stdout.splitlines() if l.startswith('{') and json.loads(l)['aggregateId']==pid and json.loads(l)['eventType']=='post.liked')
before=len(notes());subprocess.run(k+['exec','-i','deploy/kafka','--','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:19092','--producer.config','/tmp/admin.properties','--topic','network.events.v1','--property','parse.key=true'],input=pid+'\t'+json.dumps(e)+'\n',text=True,check=True,capture_output=True)
request('POST','/api/v1/posts/'+pid+'/comments',b,{'body':'Kind replay barrier'});wait_count(before+1)
assert len([n for n in notes() if n['eventType']=='post.liked'])==1
subprocess.run(k+['scale','deployment/content-service','--replicas=2'],check=True)
try:
 subprocess.run(k+['rollout','status','deployment/content-service','--timeout=180s'],check=True)
 deployment=json.loads(subprocess.check_output(k+['get','deployment/content-service','-o','json'],text=True));assert deployment['status']['readyReplicas']==2
 before=len(notes())
 for i in range(20):request('POST','/api/v1/posts/'+pid+'/comments',b,{'body':'Two ready relays '+str(i)})
 wait_count(before+20)
 print('PASS: kind replay deduplication; twenty actions with two ready content/relay replicas')
finally:
 subprocess.run(k+['scale','deployment/content-service','--replicas=1'],check=True)
 subprocess.run(k+['rollout','status','deployment/content-service','--timeout=180s'],check=True)

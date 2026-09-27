#!/usr/bin/env python3
"""Disruptive checks, only for the dedicated professional-network-mvp Compose project."""
import json,subprocess,time,urllib.request,urllib.parse,urllib.error
from pathlib import Path
exec(open('scripts/check-foundation.py').read())
a,b,c=session['users'];pid=session['postId'];path='/api/v1/posts/'+pid

def compose(*args,input=None):return subprocess.run(['docker','compose',*args],input=input,text=True,check=True,capture_output=True).stdout

def wait_for(predicate,label,seconds=90):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  try:
   result=predicate()
   if result:return result
  except (urllib.error.URLError,AssertionError):pass
  time.sleep(.5)
 raise AssertionError('Timeout: '+label)

def notifications():
 code,body=call('GET','/api/v1/notifications?size=100',a['access_token']);assert code==200
 return [n for n in body if n['resourceId']==pid]

def read_events():
 r=subprocess.run(['docker','compose','exec','-T','kafka','/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:19092','--topic','network.events.v1','--from-beginning','--consumer.config','/tmp/admin.properties','--timeout-ms','8000'],capture_output=True,text=True,timeout=30)
 return [json.loads(line) for line in r.stdout.splitlines() if line.startswith('{')]

def publish(event):
 compose('exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:19092','--topic','network.events.v1','--producer.config','/tmp/admin.properties','--property','parse.key=true',input=event['aggregateId']+'\t'+json.dumps(event)+'\n')

original=next(e for e in read_events() if e['aggregateId']==pid and e['eventType']=='post.liked')
count=len(notifications());publish(original)
# Publish a distinct action after the duplicate on the same aggregate key; seeing
# its notification proves the earlier replay has passed through the partition.
assert call('POST',path+'/comments',b['access_token'],{'body':'Replay processing barrier'})[0]==200
wait_for(lambda:len(notifications())==count+1,'replay barrier')
assert len([n for n in notifications() if n['eventType']=='post.liked'])==1
print('PASS: real Kafka event replay deduplicated')
count=len(notifications())
compose('stop','kafka')
try:
 started=time.monotonic();status,result=call('POST',path+'/comments',b['access_token'],{'body':'Committed during broker outage'});assert status==200,(status,result)
 assert time.monotonic()-started<5,'Business transaction waited for Kafka'
 assert len(notifications())==count
 print('PASS: business write commits during broker outage')
finally:compose('start','kafka')
wait_for(lambda:len(notifications())==count+1,'outbox broker recovery',120)
print('PASS: outbox delivers after broker recovery')
count=len(notifications());compose('stop','notification-service')
try:assert call('POST',path+'/comments',b['access_token'],{'body':'Consumer restart recovery'})[0]==200
finally:compose('start','notification-service')
wait_for(lambda:len(notifications())==count+1,'consumer recovery')
print('PASS: consumer restart preserves and catches up notifications')
count=len(notifications())
compose('run','-d','--no-deps','--name','professional-network-mvp-relay-check','content-service')
try:
 wait_for(lambda:subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}','professional-network-mvp-relay-check'],text=True).strip()=='healthy','second relay readiness')
 for i in range(10):assert call('POST',path+'/comments',b['access_token'],{'body':'Concurrent relay '+str(i)})[0]==200
 wait_for(lambda:len(notifications())==count+10,'two relay instances')
 assert len({n['id'] for n in notifications()})==count+10
 print('PASS: concurrent relay instances deliver ten actions without duplicate notifications')
finally:subprocess.run(['docker','rm','-f','professional-network-mvp-relay-check'],check=True,capture_output=True)
Path('.local/replay-event.json').write_text(json.dumps(original))

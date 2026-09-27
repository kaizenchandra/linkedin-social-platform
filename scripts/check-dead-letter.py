#!/usr/bin/env python3
import json,subprocess,time,uuid,urllib.request
from pathlib import Path
s=json.loads(Path('.local/session.json').read_text());event=json.loads(Path('.local/replay-event.json').read_text());event['eventId']=str(uuid.uuid4());event['schemaVersion']=99
base=['docker','compose','exec','-T','kafka']
def publish(e):
 subprocess.run(base+['/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:19092','--producer.config','/tmp/admin.properties','--topic','network.events.v1','--property','parse.key=true'],input=e['aggregateId']+'\t'+json.dumps(e)+'\n',text=True,check=True,capture_output=True)
def notes():
 with urllib.request.urlopen(urllib.request.Request('http://localhost:8080/api/v1/notifications?size=100',headers={'Authorization':'Bearer '+s['users'][0]['access_token']}),timeout=5) as r:return json.load(r)
count=len(notes());publish(event)
end=time.monotonic()+60
while time.monotonic()<end:
 r=subprocess.run(base+['/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:19092','--consumer.config','/tmp/admin.properties','--topic','network.events.v1.DLT','--from-beginning','--timeout-ms','8000'],capture_output=True,text=True,timeout=20)
 events=[json.loads(l) for l in r.stdout.splitlines() if l.startswith('{')]
 if any(e['eventId']==event['eventId'] for e in events):break
else:raise AssertionError('Invalid schema did not reach DLT')
assert len(notes())==count
# Operator repair preserves eventId; the failed transaction did not claim dedup.
event['schemaVersion']=1;publish(event)
end=time.monotonic()+30
while time.monotonic()<end:
 if len(notes())==count+1:break
 time.sleep(.25)
else:raise AssertionError('Corrected replay did not create notification')
print('PASS: invalid schema retries then DLT; corrected same-eventId replay commits once')

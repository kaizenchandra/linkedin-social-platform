#!/usr/bin/env python3
from hiring_http import *
import subprocess
base=['scripts/kubectl-local.sh','-n','network-mvp','exec','-i','deploy/kafka','--']
result=subprocess.run(base+['/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:19092','--consumer.config','/tmp/admin.properties','--topic','network.events.v1','--from-beginning','--timeout-ms','5000'],text=True,capture_output=True,timeout=25)
events=[json.loads(l) for l in result.stdout.splitlines() if l.startswith('{')]
event=next(e for e in events if e['aggregateId']==session['applicationId'] and e['eventType']=='hiring.application.status')
def count():return len([n for n in ok('GET','/api/v1/notifications?size=100',users['alice']) if n['resourceId']==session['applicationId']])
before=count();barrier={**event,'eventId':str(uuid.uuid4())}
subprocess.run(base+['/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:19092','--producer.config','/tmp/admin.properties','--topic','network.events.v1','--property','parse.key=true'],input=''.join(e['aggregateId']+'\t'+json.dumps(e)+'\n' for e in [event,barrier]),text=True,capture_output=True,check=True,timeout=30)
end=time.monotonic()+60
while time.monotonic()<end:
 value=count()
 if value==before+1:break
 assert value<=before+1,'Duplicate hiring notification'
 time.sleep(.25)
else:raise AssertionError('Same-key replay barrier missing')
print('PASS: kind real hiring event replay deduplication with same-key barrier')

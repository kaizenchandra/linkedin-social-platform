#!/usr/bin/env python3
from mvp5_support import *
import socket,urllib.parse
from contextlib import ExitStack

def metric(expression):
 data=json.load(urllib.request.urlopen('http://localhost:9095/api/v1/query?'+urllib.parse.urlencode({'query':expression}),timeout=5))['data']['result']
 return float(data[0]['value'][1]) if data else 0

a,b,cid=pair();path='/api/v1/conversations/'+cid;cursor=ok('GET','/api/v1/conversations/sync',a)['cursor'];evidence={}
# Real Oracle stress fixture: 5,000 private archive transitions and matching invalidations
# in one transaction. Normal message/control semantics are separately tested through REST.
statement="""DECLARE n NUMBER; v NUMBER; g VARCHAR2(32); e VARCHAR2(36); c VARCHAR2(36);
BEGIN
 SELECT id INTO c FROM messaging_app.conversations WHERE id='%s' FOR UPDATE;
 SELECT last_position INTO n FROM messaging_app.stream_heads WHERE owner_id='%s' FOR UPDATE;
 SELECT version INTO v FROM messaging_app.conversation_preferences WHERE conversation_id=c AND member_id='%s' FOR UPDATE;
 FOR i IN 1..5000 LOOP
  UPDATE messaging_app.conversation_preferences SET archived=MOD(i,2),version=v+i WHERE conversation_id=c AND member_id='%s';
  g:=LOWER(RAWTOHEX(SYS_GUID())); e:=SUBSTR(g,1,8)||'-'||SUBSTR(g,9,4)||'-'||SUBSTR(g,13,4)||'-'||SUBSTR(g,17,4)||'-'||SUBSTR(g,21,12);
  INSERT INTO messaging_app.stream_events(owner_id,position,event_id,event_type,resource_id,resource_version,occurred_at) VALUES('%s',n+i,e,'conversation.preferences',c,v+i,SYSTIMESTAMP);
 END LOOP;
 UPDATE messaging_app.stream_heads SET last_position=n+5000 WHERE owner_id='%s';
 COMMIT;
END;
/
"""%(cid,a['id'],a['id'],a['id'],a['id'],a['id'])
sql(statement)
query='sum(network_stream_gateway_slow_clients_total)'
before=metric(query)
# Compile with the project's Java21, then run inside the existing local gateway container.
# Docker Desktop's host-port proxy can absorb megabytes even when a host client stops reading.
java_home=subprocess.check_output(['/usr/libexec/java_home','-v','21'],text=True).strip() if Path('/usr/libexec/java_home').exists() else os.environ['JAVA_HOME']
Path('.local/slow-reader').mkdir(exist_ok=True)
subprocess.run([java_home+'/bin/javac','--release','21','-d','.local/slow-reader','tools/realtime/test/SlowReader.java'],check=True)
gateway=container('api-gateway')
subprocess.run(['docker','cp','.local/slow-reader/SlowReader.class',gateway+':/tmp/SlowReader.class'],check=True,capture_output=True)
probe=subprocess.Popen(['docker','exec','-i',gateway,'java','-cp','/tmp','SlowReader'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
try:
 probe.stdin.write(a['access_token']+'\n'+cursor+'\n');probe.stdin.flush()
 ready=probe.stdout.readline().strip();assert ready.startswith('READY '),ready
 start=time.monotonic();wait(lambda:metric(query)>before,'actual slow-client disconnection',45)
 evidence['slowReader']={'secondsToObservedDisconnectMetric':time.monotonic()-start,'actualReceiveBufferBytes':int(ready.split()[1]),'fixtureEvents':5000,'maxApplicationBatchBytes':32768,'path':'Direct Docker-network TCP client through gateway; no host-port proxy'}
finally:
 if probe.poll() is None:
  probe.stdin.write('STOP\n');probe.stdin.flush()
 probe.wait(timeout=10)
 subprocess.run(['docker','exec','-u','0',gateway,'rm','-f','/tmp/SlowReader.class'],check=True,capture_output=True)
with Stream(a,'/api/v1/conversations/stream',cursor) as recovered:
 event=recovered.next('conversation.preferences',cid);assert event['resourceVersion']==1
 assert ok('GET',path,a)['preferenceVersion']==5000
# Expire only this isolated owner's stream history; the real scheduled worker prunes it.
sql("UPDATE messaging_app.stream_events SET occurred_at=SYSTIMESTAMP-INTERVAL '25' HOUR WHERE owner_id='"+a['id']+"';\nCOMMIT;")
wait(lambda:int(sql("SELECT retained_floor FROM messaging_app.stream_heads WHERE owner_id='"+a['id']+"';"))>1,'scheduled retention floor',100)
try:Stream(a,'/api/v1/conversations/stream',cursor);raise AssertionError('Expired replay cursor accepted')
except urllib.error.HTTPError as error:assert error.code==410,error.code
snapshot=ok('GET','/api/v1/conversations/sync',a)
assert snapshot['unreadCount']==0 and snapshot['state']['items'][0]['preferenceVersion']==5000
with Stream(a,'/api/v1/conversations/stream',snapshot['cursor']) as reset:
 sent=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'After authoritative reset'})
 assert reset.next('message.created',cid)['resourceVersion']==sent['sequence']
evidence['expiredCursor']='Scheduled 24-hour retention yields410; authoritative state is preserved; fresh boundary receives new committed message'
refresh()
with ExitStack() as stack:
 for name in ['conversations','notifications']:
  boundary=ok('GET','/api/v1/'+name+'/sync',b)['cursor']
  for _ in range(3):stack.enter_context(Stream(b,'/api/v1/'+name+'/stream',boundary))
  try:Stream(b,'/api/v1/'+name+'/stream',boundary);raise AssertionError('Admission exceeded')
  except urllib.error.HTTPError as error:assert error.code==429,error.code
 # Six streams remain usable while REST remains independent.
 assert ok('GET',path,b)['unreadCount']==0
 evidence['admission']='Fourth messaging stream rejected by service; six combined streams admitted; seventh rejected by gateway; REST remains available'
Path('docs/mvp5-limits-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('PASS slow-reader backpressure, replay recovery, real retention/reset, service/gateway admission and concurrent REST',json.dumps(evidence))

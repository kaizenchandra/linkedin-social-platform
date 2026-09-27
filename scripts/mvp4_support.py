"""Local failure-test helpers; metadata-only SQL and bounded condition waits."""
from hiring_http import *
import subprocess,os

def wait(check,label,timeout=150):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if check():return
  time.sleep(.25)
 raise AssertionError('Timeout: '+label)
def compose(*args):
 if os.getenv('MVP4_BACKEND')=='kind':
  assert args[0] in ['stop','start'],args
  service=args[-1];prefix=['scripts/kubectl-local.sh','-n','network-mvp']
  replicas=next(item['spec']['replicas'] for item in json.loads(Path('infra/k8s/applications.json').read_text())['items'] if item['kind']=='Deployment' and item['metadata']['name']==service)
  subprocess.run(prefix+['scale','deployment/'+service,'--replicas='+('0' if args[0]=='stop' else str(replicas))],check=True,capture_output=True)
  if args[0]=='start':subprocess.run(prefix+['rollout','status','deployment/'+service,'--timeout=180s'],check=True,capture_output=True)
  else:subprocess.run(prefix+['wait','--for=delete','pod','-l','app='+service,'--timeout=90s'],check=True,capture_output=True)
 else:subprocess.run(['docker','compose',*args],check=True,capture_output=True)
def kafka_prefix():
 return ['scripts/kubectl-local.sh','-n','network-mvp','exec','-i','deploy/kafka','--'] if os.getenv('MVP4_BACKEND')=='kind' else ['docker','compose','exec','-T','kafka']
def sql(statement):
 r=subprocess.run(['docker','compose','exec','-T','-e','ORACLE_PDB_SID=FREEPDB1','oracle','sqlplus','-s','/','as','sysdba'],input='WHENEVER SQLERROR EXIT SQL.SQLCODE\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 32767\n'+statement+'\nEXIT\n',text=True,capture_output=True,check=True);return r.stdout.strip()
def publish_event(event):
 subprocess.run(kafka_prefix()+['/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:19092','--producer.config','/tmp/admin.properties','--topic','network.events.v1','--property','parse.key=true','--property','key.separator=|'],input=event['aggregateId']+'|'+json.dumps(event)+'\n',text=True,check=True,capture_output=True)
def events(topic='network.events.v1'):
 r=subprocess.run(kafka_prefix()+['/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:19092','--consumer.config','/tmp/admin.properties','--topic',topic,'--from-beginning','--timeout-ms','5000'],capture_output=True,text=True,timeout=30)
 return [json.loads(x) for x in r.stdout.splitlines() if x.startswith('{')]
def count(user,job_id):return sum(n['eventType']=='hiring.job.alert' and n['resourceId']==job_id for n in ok('GET','/api/v1/notifications?size=100',user))
def refresh():
 import urllib.parse
 for u in users.values():
  if u.get('refresh_token'):
   data=urllib.parse.urlencode({'grant_type':'refresh_token','client_id':session['client'],'refresh_token':u['refresh_token']}).encode()
   with urllib.request.urlopen('http://localhost:8180/realms/network/protocol/openid-connect/token',data,timeout=10) as r:u.update(json.load(r))
 save()

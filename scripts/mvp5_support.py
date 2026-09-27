"""Dedicated local MVP-5 fixtures. Peer ports are random loopback ports, not shared services."""
from mvp4_support import *
from sse_client import Stream
from contextlib import contextmanager

def call_at(base,method,path,user,data=None):
 headers={'Content-Type':'application/json'}
 if user:headers['Authorization']='Bearer '+user['access_token']
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,None if data is None else json.dumps(data).encode(),headers,method=method),timeout=15) as response:return response.status,json.loads(response.read() or 'null')
 except urllib.error.HTTPError as error:return error.code,json.loads(error.read() or 'null')
def ok_at(base,method,path,user,data=None,expected=200):
 status,value=call_at(base,method,path,user,data);assert status==expected,(method,path,status,value);return value

def pair():
 refresh();profiles();a=users['alice'];b=users['bob']
 for u,v in [(a,b),(b,a)]:ok('DELETE','/api/v1/blocks/'+v['id'],u,expected=204)
 connection=ok('POST','/api/v1/connections',a,{'targetId':b['id']});ok('POST','/api/v1/connections/'+connection['id']+'/accept',b)
 cid=ok('POST','/api/v1/conversations',a,{'memberId':b['id']})['id'];return a,b,cid

def container(service):return subprocess.check_output(['docker','compose','ps','-q',service],text=True).strip()
def healthy(name):
 state=json.loads(subprocess.check_output(['docker','inspect','--format','{{json .State}}',name],text=True))
 if state['Status']=='exited':raise AssertionError('Fixture container exited: '+name)
 return state.get('Health',{}).get('Status')=='healthy'
def port(name):
 return 'http://'+subprocess.check_output(['docker','port',name,'8080/tcp'],text=True).strip()

@contextmanager
def peers():
 assert os.getenv('MVP4_BACKEND')!='kind','Compose peer fixtures require the dedicated Compose environment'
 names=[];suffix=uuid.uuid4().hex[:8];values={}
 try:
  for service in ['messaging-service','notification-service','api-gateway']:
   name='pn-mvp5-'+service+'-'+suffix
   args=['docker','compose','run','-d','--no-deps','--name',name,'-p','127.0.0.1::8080']
   if service!='messaging-service':args+=['-e','MESSAGING_URL=http://'+values['messaging-service']['name']+':8080']
   if service=='api-gateway':args+=['-e','NOTIFICATION_URL=http://'+values['notification-service']['name']+':8080']
   subprocess.run(args+[service],check=True,capture_output=True);names.append(name)
   wait(lambda:healthy(name),'peer healthy '+service,180)
   values[service]={'name':name,'base':port(name)}
  yield values
 finally:
  for name in reversed(names):subprocess.run(['docker','rm','-f',name],check=True,capture_output=True)

def event_for(cid,sequence):
 return next(e for e in events() if e['eventType']=='message.sent' and e['aggregateId']==cid and e['aggregateVersion']==sequence)
def delivered(event):
 return sql("SELECT COUNT(*) FROM notification_app.notifications WHERE event_id='"+event['eventId']+"';")=='1'

def sql(statement):
 if os.getenv('MVP4_BACKEND')!='kind':
  from mvp4_support import sql as compose_sql
  return compose_sql(statement)
 command=['scripts/kubectl-local.sh','-n','network-mvp','exec','-i','deploy/oracle','--','sqlplus','-s','/','as','sysdba']
 result=subprocess.run(command,input='WHENEVER SQLERROR EXIT SQL.SQLCODE\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 32767\nALTER SESSION SET CONTAINER=FREEPDB1;\n'+statement+'\nEXIT\n',text=True,capture_output=True,check=True)
 return result.stdout.strip()

@contextmanager
def internal_base(service,compose_port):
 if os.getenv('MVP4_BACKEND')!='kind':
  yield 'http://localhost:'+str(compose_port);return
 import select,re
 command=['scripts/kubectl-local.sh','-n','network-mvp','port-forward','service/'+service,'0:8080','--address=127.0.0.1']
 process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 try:
  assert select.select([process.stdout],[],[],15)[0],'Internal port-forward not ready'
  line=process.stdout.readline();match=re.search(r'127.0.0.1:(\d+)',line);assert match,line
  yield 'http://127.0.0.1:'+match.group(1)
 finally:process.terminate();process.wait(timeout=10)

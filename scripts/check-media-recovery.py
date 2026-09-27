#!/usr/bin/env python3
"""Simulate a crashed media process by seeding only isolated test claim rows."""
exec(open('scripts/check-mvp2-media.py').read().split("assert upload(a,b'invalid bytes')")[0])
import subprocess,urllib.parse
v=dict(x.split('=',1) for x in Path('.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
form=urllib.parse.urlencode({'grant_type':'client_credentials','client_id':'media-internal','client_secret':v['MEDIA_CLIENT_SECRET']}).encode()
with urllib.request.urlopen(urllib.request.Request('http://localhost:8180/realms/network/protocol/openid-connect/token',form),timeout=10) as r:service_token=json.load(r)['access_token']
def owner(action,operation):
 try:
  with urllib.request.urlopen(urllib.request.Request('http://localhost:8082/internal/v1/media/'+action,json.dumps(operation).encode(),{'Authorization':'Bearer '+service_token,'Content-Type':'application/json'},method='POST'),timeout=10) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,e.read().decode()
def stale_claim(mid,op):
 # Every interpolated value is a server/client UUID from this isolated fixture.
 for value in [mid,op['resourceId'],op['operationId']]:uuid.UUID(value)
 sql="ALTER SESSION SET CONTAINER=FREEPDB1;\nUPDATE media_app.media_objects SET state='CLAIMED',resource_type='POST',resource_id='%s',operation_id='%s',operation_media_ids='%s',updated_at=SYSTIMESTAMP-INTERVAL '2' DAY WHERE id='%s';\nCOMMIT;\n"%(op['resourceId'],op['operationId'],mid,mid)
 subprocess.run(['docker','compose','exec','-T','oracle','sqlplus','-s','/','as','sysdba'],input='WHENEVER SQLERROR EXIT SQL.SQLCODE\n'+sql,text=True,check=True,capture_output=True)
fixtures=[]
for commit in [True,False]:
 status,m=upload(a,png());assert status==200,(status,m)
 status,p=call('POST','/api/v1/posts',a['access_token'],{'body':'Interrupted attachment fixture'});assert status==200
 op={'operationId':str(uuid.uuid4()),'resourceId':p['id'],'actorId':a['id'],'mediaIds':[m['id']]}
 assert owner('prepare',op)[0]==200
 stale_claim(m['id'],op)
 if commit:assert owner('commit',op)[0]==200
 fixtures.append((commit,m['id'],op))
end=time.monotonic()+100
while time.monotonic()<end:
 states=[call('GET','/api/v1/media/'+mid,a['access_token']) for _,mid,_ in fixtures]
 if states[0][0]==200 and states[0][1]['state']=='ATTACHED' and states[1][0]==404:break
 time.sleep(.5)
else:raise AssertionError(('Reconciliation did not converge',states))
assert download(a,fixtures[0][1])[0]==200
assert owner('commit',fixtures[1][2])[0]==409
print('PASS: real worker completes committed claim; aborts abandoned owner intent before deletion; late commit conflicts; valid attachment remains readable')

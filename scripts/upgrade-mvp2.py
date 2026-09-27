#!/usr/bin/env python3
"""Idempotent provisioning for the dedicated local Compose database/identity provider."""
import json,os,sys,subprocess,time,http.client,urllib.request,urllib.parse
from pathlib import Path
env=dict(x.split('=',1) for x in Path('.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
if len(sys.argv)>1 and sys.argv[1]=='kind':
 args=['scripts/kubectl-local.sh','-n','network-mvp','exec','-i','deploy/oracle','--','bash','-s']
else:
 args=['docker','compose','exec','-T']
 for key in ['MEDIA_DB_PASSWORD','MEDIA_RUNTIME_PASSWORD','MESSAGING_DB_PASSWORD','MESSAGING_RUNTIME_PASSWORD']:args+=['-e',key]
 args+=['oracle','bash','-s']
subprocess.run(args,input=Path('infra/oracle/002-mvp2-users.sh').read_text(),text=True,env={**os.environ,**env},check=True)
base='http://localhost:8180'
def req(method,path,data=None,token=None,form=False):
 raw=None if data is None else (urllib.parse.urlencode(data).encode() if form else json.dumps(data).encode());headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
 if token:headers['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,raw,headers,method=method),timeout=15) as r:
  b=r.read();return json.loads(b) if b else None
# Pod readiness can precede host NodePort availability. Poll the public discovery
# document before performing admin mutations; never blindly retry a mutation.
end=time.monotonic()+120
while time.monotonic()<end:
 try:
  discovery=req('GET','/realms/network/.well-known/openid-configuration')
  if discovery.get('issuer')==base+'/realms/network':break
 except (OSError,http.client.HTTPException):pass
 time.sleep(.5)
else:raise SystemExit('Local Keycloak discovery did not become available in 120 seconds')
admin=req('POST','/realms/master/protocol/openid-connect/token',{'grant_type':'password','client_id':'admin-cli','username':'admin','password':env['KEYCLOAK_ADMIN_PASSWORD']},form=True)['access_token']
realm=json.loads(Path('infra/keycloak/network-realm.json').read_text());prefix='/admin/realms/network'
scopes={s['name']:s['id'] for s in req('GET',prefix+'/client-scopes',token=admin)}
for s in realm['clientScopes']:
 if s['name'] not in scopes:req('POST',prefix+'/client-scopes',s,admin)
scopes={s['name']:s['id'] for s in req('GET',prefix+'/client-scopes',token=admin)}
for c in realm['clients']:
 if c['clientId'] not in ['media-internal','messaging-internal']:continue
 c=dict(c);key=c['secret'][2:-1];c['secret']=env[key]
 existing=req('GET',prefix+'/clients?clientId='+c['clientId'],token=admin)
 if not existing:req('POST',prefix+'/clients',c,admin)
 client=req('GET',prefix+'/clients?clientId='+c['clientId'],token=admin)[0]
 for scope in c['defaultClientScopes']:req('PUT',prefix+'/clients/'+client['id']+'/default-client-scopes/'+scopes[scope],token=admin)
print('MVP-2 schemas and scoped identity clients ready; existing data preserved')

roles={r['name'] for r in req('GET',prefix+'/roles',token=admin)}
if 'moderator' not in roles:req('POST',prefix+'/roles',{'name':'moderator','description':'Explicit content moderation only'},admin)
mapper=next(x for x in realm['clientScopes'] if x['name']=='network-audience')['protocolMappers'][-1]
existing=req('GET',prefix+'/client-scopes/'+scopes['network-audience']+'/protocol-mappers/models',token=admin)
if not any(m['name']==mapper['name'] for m in existing):req('POST',prefix+'/client-scopes/'+scopes['network-audience']+'/protocol-mappers/models',mapper,admin)
print('Trusted moderator role and signed realm-role mapper ready; no users granted moderation automatically')

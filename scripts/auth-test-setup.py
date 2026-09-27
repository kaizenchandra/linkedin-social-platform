#!/usr/bin/env python3
"""Provision isolated test identities and a test-only direct-grant client via Keycloak Admin REST.
Secrets/tokens are written only to .local/session.json (0600), never stdout.
The interactive network-web client remains PKCE-only.
"""
import json,urllib.request,urllib.parse,os,secrets
from pathlib import Path
env=dict(line.strip().split('=',1) for line in Path('.env').read_text().splitlines() if line and not line.startswith('#'))
base=os.getenv('KEYCLOAK_URL','http://localhost:8180')
def req(method,url,data=None,token=None,form=False):
 raw=None if data is None else (urllib.parse.urlencode(data).encode() if form else json.dumps(data).encode())
 headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
 if token:headers['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(url,raw,headers,method=method),timeout=15) as r:
  text=r.read();return json.loads(text) if text else None
admin=req('POST',base+'/realms/master/protocol/openid-connect/token',{'grant_type':'password','client_id':'admin-cli','username':'admin','password':env['KEYCLOAK_ADMIN_PASSWORD']},form=True)['access_token']
run=secrets.token_hex(5);client='smoke-'+run
req('POST',base+'/admin/realms/network/clients',{'clientId':client,'publicClient':True,'directAccessGrantsEnabled':True,'standardFlowEnabled':False,'defaultClientScopes':['network-audience']},admin)
users=[]
for name in os.getenv('TEST_MEMBER_NAMES','alice,bob,charlie').split(','):
 username=name+'-'+run;password=secrets.token_hex(24)
 req('POST',base+'/admin/realms/network/users',{'username':username,'enabled':True,'emailVerified':True,'firstName':name,'lastName':'Smoke','email':username+'@example.invalid','credentials':[{'type':'password','value':password,'temporary':False}]},admin)
 u=req('GET',base+'/admin/realms/network/users?username='+username,token=admin)[0]
 token=req('POST',base+'/realms/network/protocol/openid-connect/token',{'grant_type':'password','client_id':client,'username':username,'password':password},form=True)
 users.append({'name':name,'id':u['id'],'username':username,'password':password,**token})
Path('.local').mkdir(exist_ok=True);path=Path(os.getenv('TEST_SESSION','.local/session.json'));path.write_text(json.dumps({'client':client,'users':users}));os.chmod(path,0o600)
print('Created isolated test identities; tokens stored in private local session file')

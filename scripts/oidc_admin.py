"""Supported admin API for the dedicated local identity provider; never emits credentials."""
import json,urllib.request,urllib.parse
from pathlib import Path
env=dict(line.split('=',1) for line in Path('.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
base='http://localhost:8180'
def request(method,path,data=None,token=None,form=False):
 raw=None if data is None else (urllib.parse.urlencode(data).encode() if form else json.dumps(data).encode())
 headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
 if token:headers['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,raw,headers,method=method),timeout=15) as response:
  body=response.read();return json.loads(body) if body else None
def admin():return request('POST','/realms/master/protocol/openid-connect/token',{'grant_type':'password','client_id':'admin-cli','username':'admin','password':env['KEYCLOAK_ADMIN_PASSWORD']},form=True)['access_token']
def client(name,token):return request('GET','/admin/realms/network/clients?'+urllib.parse.urlencode({'clientId':name}),token=token)[0]

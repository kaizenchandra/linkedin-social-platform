#!/usr/bin/env python3
from mvp4_support import *
from sse_client import Stream
import oidc_admin,copy,base64
refresh();a=users['alice'];cursors={name:ok('GET','/api/v1/'+name+'/sync',a)['cursor'] for name in ['conversations','notifications']}
admin=oidc_admin.admin();original=oidc_admin.client(session['client'],admin);changed=copy.deepcopy(original);changed.setdefault('attributes',{})['access.token.lifespan']='5'
path='/admin/realms/network/clients/'+original['id']
try:
 oidc_admin.request('PUT',path,changed,admin)
 token=oidc_admin.request('POST','/realms/network/protocol/openid-connect/token',{'grant_type':'refresh_token','client_id':session['client'],'refresh_token':a['refresh_token']},form=True)
finally:oidc_admin.request('PUT',path,original,admin)
part=token['access_token'].split('.')[1];claims=json.loads(base64.urlsafe_b64decode(part+'='*(-len(part)%4)));assert claims['exp']-claims['iat']==5
from contextlib import ExitStack
with ExitStack() as stack:
 streams=[stack.enter_context(Stream({**a,**token},'/api/v1/'+name+'/stream',cursor)) for name,cursor in cursors.items()]
 for stream in streams:
  assert stream.closed.wait(timeout=10),'Stream survived token expiry'
  assert time.time()<claims['exp']+3,'Stream expiry was not bounded'
for name,cursor in cursors.items():
 try:Stream({**a,**token},'/api/v1/'+name+'/stream',cursor);raise AssertionError('Expired token admitted')
 except urllib.error.HTTPError as e:assert e.code==401,e.code
print('PASS real Keycloak five-second token closes both streams and is rejected on reconnect; original test-client settings restored')

#!/usr/bin/env python3
"""Assign the trusted role only through local Keycloak Admin REST to a new test identity."""
exec(open('scripts/auth-test-setup.py').read().split('run=secrets.token_hex')[0])
s=json.loads(Path(os.getenv('TEST_SESSION','.local/session.json')).read_text());username='moderator-'+secrets.token_hex(5);password=secrets.token_hex(24)
req('POST',base+'/admin/realms/network/users',{'username':username,'enabled':True,'emailVerified':True,'firstName':'Moderator','lastName':'Smoke','email':username+'@example.invalid','credentials':[{'type':'password','value':password,'temporary':False}]},admin)
u=req('GET',base+'/admin/realms/network/users?username='+username,token=admin)[0]
role=req('GET',base+'/admin/realms/network/roles/moderator',token=admin)
req('POST',base+'/admin/realms/network/users/'+u['id']+'/role-mappings/realm',[role],admin)
token=req('POST',base+'/realms/network/protocol/openid-connect/token',{'grant_type':'password','client_id':s['client'],'username':username,'password':password},form=True)
s['moderator']={'id':u['id'],'name':'moderator','username':username,'password':password,**token};Path(os.getenv('TEST_SESSION','.local/session.json')).write_text(json.dumps(s));os.chmod(os.getenv('TEST_SESSION','.local/session.json'),0o600)
print('Created isolated moderator test identity through identity-provider admin role assignment')

#!/usr/bin/env python3
"""Add only the new notification-service scope in the dedicated local Keycloak realm."""
import oidc_admin as oidc
admin=oidc.admin();prefix='/admin/realms/network'
scopes={s['name']:s['id'] for s in oidc.request('GET',prefix+'/client-scopes',token=admin)}
name='messaging.notifications'
if name not in scopes:
 oidc.request('POST',prefix+'/client-scopes',{'name':name,'protocol':'openid-connect','attributes':{'include.in.token.scope':'true'}},admin)
 scopes={s['name']:s['id'] for s in oidc.request('GET',prefix+'/client-scopes',token=admin)}
client=oidc.client('notification-internal',admin)
oidc.request('PUT',prefix+'/clients/'+client['id']+'/default-client-scopes/'+scopes[name],token=admin)
print('PASS scoped messaging preference authority provisioned for notification service only')

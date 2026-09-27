#!/usr/bin/env python3
exec(open('scripts/check-foundation.py').read())
a,b,c=session['users']
status,rel=call('POST','/api/v1/connections',a['access_token'],{'targetId':b['id']});assert status==200,(status,rel)
rid=rel['id']
assert call('POST','/api/v1/connections',b['access_token'],{'targetId':a['id']})[0]==409
assert call('POST','/api/v1/connections/'+rid+'/accept',c['access_token'])[0]==404
assert call('POST','/api/v1/connections/'+rid+'/accept',a['access_token'])[0]==409
assert call('POST','/api/v1/connections/'+rid+'/accept',b['access_token'])[0]==200
assert call('POST','/api/v1/connections/'+rid+'/accept',b['access_token'])[0]==200
assert len(call('GET','/api/v1/connections',a['access_token'])[1])==1
session['connectionId']=rid;Path('.local/session.json').write_text(json.dumps(session))
print('PASS: reciprocal conflict, third-party denial, recipient-only/idempotent acceptance and accepted list')

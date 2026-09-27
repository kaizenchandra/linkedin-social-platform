#!/usr/bin/env python3
from mvp4_support import *
refresh();profiles();a=users['alice'];b=users['bob']
for u,v in [(a,b),(b,a)]:ok('DELETE','/api/v1/blocks/'+v['id'],u,expected=204)
p=ok('POST','/api/v1/posts',b,{'body':'Authorization outage fixture','visibility':'MEMBERS'})
ok('PUT','/api/v1/posts/'+p['id']+'/saved',a,expected=204)
compose('stop','member-service')
try:
 ok('GET','/api/v1/feed',a,expected=503)
 ok('GET','/api/v1/posts/saved',a,expected=503)
finally:compose('start','--wait','--wait-timeout','150','member-service')
assert any(x['post']['id']==p['id'] for x in ok('GET','/api/v1/posts/saved?size=100',a)['items'])
print('PASS authoritative member outage returns503 for full feed and private saved listing, then recovers')

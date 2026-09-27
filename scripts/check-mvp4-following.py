#!/usr/bin/env python3
from hiring_http import *
profiles();a=users['alice'];b=users['bob'];owner=users['owner']
for u,v in [(a,b),(b,a)]:
 ok('DELETE','/api/v1/blocks/'+v['id'],u,expected=204)
 ok('PUT','/api/v1/members/'+v['id']+'/follow',u,expected=204)
 ok('PUT','/api/v1/members/'+v['id']+'/follow',u,expected=204)
 assert ok('GET','/api/v1/members/'+v['id']+'/follow',u)['following']
 assert sum(x['memberId']==v['id'] for x in ok('GET','/api/v1/members/me/following',u))==1
ok('PUT','/api/v1/blocks/'+b['id'],a,expected=204)
for u,v in [(a,b),(b,a)]:assert not ok('GET','/api/v1/members/'+v['id']+'/follow',u)['following']
ok('DELETE','/api/v1/blocks/'+b['id'],a,expected=204)
assert not ok('GET','/api/v1/members/'+b['id']+'/follow',a)['following']
c=company(owner)
for _ in range(2):ok('PUT','/api/v1/companies/'+c['id']+'/follow',a,expected=204)
assert ok('GET','/api/v1/companies/'+c['id']+'/follow',a)['following']
assert call('GET','/api/v1/companies/'+c['id']+'/members',a)[0]==404
assert call('PUT','/api/v1/members/'+b['id']+'/follow',None)[0]==401
print('PASS member/company idempotency, private lists, block cleanup, no role grant, unauthenticated denial through gateway')

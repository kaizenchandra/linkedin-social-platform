#!/usr/bin/env python3
from hiring_http import *
profiles();a=users['alice'];b=users['bob'];c=users['outsider'];owner=users['owner']
for peer in [b,c]:
 for u,v in [(a,peer),(peer,a)]:ok('DELETE','/api/v1/blocks/'+v['id'],u,expected=204)
# Remove prior connections before constructing isolated relationship fixture.
for rel in ok('GET','/api/v1/connections?state=ACCEPTED&size=100',a):
 if rel['requesterId'] in [b['id'],c['id']] or rel['recipientId'] in [b['id'],c['id']]:ok('POST','/api/v1/connections/'+rel['id']+'/remove',a)
for peer in [b,c]:ok('PUT','/api/v1/members/'+peer['id']+'/follow',a,expected=204)
r=ok('POST','/api/v1/connections',a,{'targetId':b['id']});ok('POST','/api/v1/connections/'+r['id']+'/accept',b)
p=ok('POST','/api/v1/posts',c,{'body':'MVP4 followed public','visibility':'MEMBERS'})
h=ok('POST','/api/v1/posts',c,{'body':'MVP4 followed restricted','visibility':'CONNECTIONS'})
f=ok('POST','/api/v1/posts',b,{'body':'MVP4 connected and followed','visibility':'CONNECTIONS'})
ids=[x['id'] for x in ok('GET','/api/v1/feed?size=100',a)['items']]
assert p['id'] in ids and h['id'] not in ids and ids.count(f['id'])==1
for _ in range(2):ok('PUT','/api/v1/posts/'+p['id']+'/saved',a,expected=204)
assert sum(x['post']['id']==p['id'] for x in ok('GET','/api/v1/posts/saved?size=100',a)['items'])==1
ok('PUT','/api/v1/posts/'+p['id'],c,{'body':'Now restricted','visibility':'CONNECTIONS'})
assert p['id'] not in [x['post']['id'] for x in ok('GET','/api/v1/posts/saved?size=100',a)['items']]
ok('PUT','/api/v1/blocks/'+c['id'],a,expected=204)
assert not ok('GET','/api/v1/members/'+c['id']+'/follow',a)['following']
assert not any(x['authorId']==c['id'] for x in ok('GET','/api/v1/feed?size=100',a)['items'])
company1=company(owner);j=job(owner,company1['id'])
for _ in range(2):ok('PUT','/api/v1/jobs/'+j['id']+'/saved',a,expected=204)
ok('POST','/api/v1/jobs/'+j['id']+'/close',owner)
saved=next(x for x in ok('GET','/api/v1/jobs/saved?size=100',a)['items'] if x['jobId']==j['id'])
assert saved['status']=='CLOSED' and 'description' not in saved
assert not any(x['jobId']==j['id'] for x in ok('GET','/api/v1/jobs/saved?size=100',b)['items'])
print('PASS followed visibility, deduplicated feed, current saved authorization, block exclusion, private closed saved job summary')

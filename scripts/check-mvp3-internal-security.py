#!/usr/bin/env python3
from hiring_http import *
# A signed ordinary member token cannot call either service's scoped hiring API.
for port,path,body in [(8081,'/internal/v1/hiring/profile-snapshot',{'memberId':users['alice']['id']}),(8086,'/internal/v1/hiring/recipients',{'companyId':session['companyId']})]:
 try:
  urllib.request.urlopen(urllib.request.Request('http://localhost:'+str(port)+path,json.dumps(body).encode(),{'Authorization':'Bearer '+users['alice']['access_token'],'Content-Type':'application/json'}),timeout=10)
  raise AssertionError('Internal API accepted member token')
 except urllib.error.HTTPError as e:assert e.code==403,e.code
# Forged company owner and moderator fields are rejected rather than trusted.
ok('POST','/api/v1/companies',users['alice'],{'displayName':'Forged','slug':'forged-'+uuid.uuid4().hex,'description':'Test','industry':'Test','location':'Local','ownerId':users['owner']['id']},expected=400)
print('PASS: scoped internal hiring APIs reject member tokens and forged company owner input is rejected')

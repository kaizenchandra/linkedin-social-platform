#!/usr/bin/env python3
from hiring_http import *
profiles();owner,recruiter,outsider=[users[x] for x in ['owner','recruiter','outsider']]
c=company(owner);cid=c['id'];company(outsider);assert c['verificationStatus']=='UNVERIFIED'
assert call('GET','/api/v1/companies/'+cid+'/members',outsider)[0]==404
payload={'displayName':'Company edit','slug':c['slug'],'description':'Details','industry':'Software','location':'Local'}
assert call('PUT','/api/v1/companies/'+cid,outsider,payload)[0]==404
inv=ok('POST','/api/v1/companies/'+cid+'/invitations',owner,{'memberId':recruiter['id']})
assert ok('POST','/api/v1/companies/'+cid+'/invitations',owner,{'memberId':recruiter['id']})['id']==inv['id']
assert call('POST','/api/v1/company-invitations/'+inv['id']+'/accept',outsider)[0]==404
ok('POST','/api/v1/company-invitations/'+inv['id']+'/accept',recruiter)
assert call('PUT','/api/v1/companies/'+cid,recruiter,payload)[0]==404
media=upload(owner);body=attachment(owner,cid,media['id'])
ok('POST','/api/v1/media/attachments',owner,body);ok('POST','/api/v1/media/attachments',owner,body)
assert ok('GET','/api/v1/companies/'+cid,outsider)['logoMediaId']==media['id'];assert download(outsider,media['id'])[0]==200
foreign=upload(outsider);assert call('POST','/api/v1/media/attachments',owner,attachment(owner,cid,foreign['id']))[0]==404
assert call('DELETE','/api/v1/companies/'+cid+'/members/'+owner['id'],owner)[0]==409
ok('POST','/api/v1/companies/'+cid+'/ownership',owner,{'memberId':recruiter['id']})
assert call('PUT','/api/v1/companies/'+cid,owner,payload)[0]==404
ok('PUT','/api/v1/companies/'+cid,recruiter,payload)
assert download(outsider,media['id'])[0]==200
ok('DELETE','/api/v1/companies/'+cid+'/members/'+owner['id'],recruiter,expected=204)
assert call('GET','/api/v1/companies/'+cid+'/members',owner)[0]==404
assert any(x['action']=='OWNERSHIP_TRANSFERRED' for x in ok('GET','/api/v1/companies/'+cid+'/audit',recruiter))
assert call('POST','/api/v1/companies',owner,{**payload,'slug':c['slug'].upper()})[0]==409
assert call('GET','/api/v1/companies',None)[0]==401
print('PASS: company isolation, owner-only editing/logo, idempotent invitations, recipient-only acceptance, atomic transfer, revocation, normalized slug and private logo integration')

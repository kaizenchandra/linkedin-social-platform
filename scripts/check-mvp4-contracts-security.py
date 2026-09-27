#!/usr/bin/env python3
from mvp4_support import *
from openapi_spec_validator import validate_spec
from jsonschema import Draft202012Validator,FormatChecker
refresh();spec=json.loads(Path('contracts/openapi.json').read_text());validate_spec(spec)
a=users['alice'];b=users['bob']
checks=[('/api/v1/members/'+b['id']+'/follow','FollowStatus'),('/api/v1/notifications/preferences/job-alerts','JobAlertPreference')]
for path,name in checks:
 body=ok('GET',path,a);Draft202012Validator({'$ref':'#/components/schemas/'+name,'components':spec['components']},format_checker=FormatChecker()).validate(body)
for path,name,wrapped in [('/api/v1/jobs/searches','SavedSearch',False),('/api/v1/posts/saved','SavedPost',True),('/api/v1/jobs/saved','SavedJob',True),('/api/v1/members/suggestions','MemberSuggestion',False),('/api/v1/companies/suggestions','CompanySuggestion',False)]:
 body=ok('GET',path,a)
 for item in body['items'] if wrapped else body:Draft202012Validator({'$ref':'#/components/schemas/'+name,'components':spec['components']},format_checker=FormatChecker()).validate(item)
 assert call('GET',path,None)[0]==401
schema=json.loads(Path('contracts/event-v1.schema.json').read_text())
for event in json.loads(Path('.local/mvp4-events.json').read_text()):Draft202012Validator(schema,format_checker=FormatChecker()).validate(event)
for port,path,body in [(8081,'/internal/v1/follows/'+a['id'],None),(8086,'/internal/v1/hiring/alerts/eligible',{'memberId':a['id'],'matchId':str(uuid.uuid4()),'jobId':str(uuid.uuid4())})]:
 request=urllib.request.Request('http://localhost:'+str(port)+path,None if body is None else json.dumps(body).encode(),{'Authorization':'Bearer '+a['access_token'],'Content-Type':'application/json'},method='GET' if body is None else 'POST')
 try:urllib.request.urlopen(request,timeout=10);raise AssertionError('Internal API accepted member token')
 except urllib.error.HTTPError as e:assert e.code==403,e.code
ok('POST','/api/v1/hiring/moderation/alerts/'+str(uuid.uuid4())+'/replay',a,expected=403)
ok('POST','/api/v1/jobs/searches',a,{'name':'Forged actor','alertsEnabled':True,'memberId':b['id']},expected=400)
ok('GET','/api/v1/members/suggestions?size=21',a,expected=400)
ok('GET','/api/v1/posts/saved?cursor=not-a-cursor',a,expected=400)
print('PASS MVP4 live OpenAPI/event contracts, anonymous denial, internal scopes, moderator-only replay, forged owner rejection, bounded inputs')

notes=ok('GET','/api/v1/notifications?size=100',a)
alerts=[n for n in notes if n['eventType']=='hiring.job.alert']
assert alerts and all(n.get('message')=='A new job matches your saved search.' for n in alerts)
print('PASS job alerts expose static generic text only')

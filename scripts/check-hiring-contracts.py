#!/usr/bin/env python3
from hiring_http import *
from openapi_spec_validator import validate_spec
from jsonschema import Draft202012Validator,FormatChecker
spec=json.loads(Path('contracts/openapi.json').read_text());validate_spec(spec)
paths=[('/api/v1/companies/'+session['companyId'],'Company'),('/api/v1/jobs?companyId='+session['companyId'],'JobSlice')]
if 'applicationId' in session:paths.append(('/api/v1/applications/'+session['applicationId'],'Application'))
for path,name in paths:
 body=ok('GET',path,users['alice'])
 Draft202012Validator({'$ref':'#/components/schemas/'+name,'components':spec['components']},format_checker=FormatChecker()).validate(body)
print('PASS: hiring OpenAPI structure and live company/job/application response contracts')

schema=json.loads(Path('contracts/event-v1.schema.json').read_text())
if Path('.local/hiring-replay-event.json').exists():Draft202012Validator(schema,format_checker=FormatChecker()).validate(json.loads(Path('.local/hiring-replay-event.json').read_text()))
if 'reportId' in session:
 body=ok('POST','/api/v1/hiring/moderation/reports/'+session['reportId']+'/inspect',users['moderator'],{'reason':'Contract verification'})
 Draft202012Validator({'$ref':'#/components/schemas/HiringInspection','components':spec['components']},format_checker=FormatChecker()).validate(body)
print('PASS: hiring event envelope and audited moderation response contracts')

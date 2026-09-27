#!/usr/bin/env python3
import json,urllib.request
from pathlib import Path
from openapi_spec_validator import validate_spec
from jsonschema import Draft202012Validator,FormatChecker
spec=json.loads(Path('contracts/openapi.json').read_text());validate_spec(spec)
schema=json.loads(Path('contracts/event-v1.schema.json').read_text());Draft202012Validator.check_schema(schema)
# Validate real persisted event replay evidence if present.
if Path('.local/replay-event.json').exists():Draft202012Validator(schema,format_checker=FormatChecker()).validate(json.loads(Path('.local/replay-event.json').read_text()))
session=json.loads(Path('.local/session.json').read_text());token=session['users'][0]['access_token']
for path,name in [('/api/v1/members/me','Profile'),('/api/v1/posts/'+session['postId'],'PostDetail'),('/api/v1/feed','PostSlice')]:
 with urllib.request.urlopen(urllib.request.Request('http://localhost:8080'+path,headers={'Authorization':'Bearer '+token}),timeout=10) as response:body=json.load(response)
 root={'$ref':'#/components/schemas/'+name,'components':spec['components']};Draft202012Validator(root,format_checker=FormatChecker()).validate(body)
print('PASS: OpenAPI structural validation, event schema/replay, live profile/post/feed contracts')

#!/usr/bin/env python3
from mvp5_support import *
from openapi_spec_validator import validate_spec
from jsonschema import Draft202012Validator,FormatChecker
from contextlib import ExitStack
spec=json.loads(Path('contracts/openapi.json').read_text());validate_spec(spec)
a,b,cid=pair();schema=json.loads(Path('contracts/stream-event-v1.schema.json').read_text());validator=Draft202012Validator(schema,format_checker=FormatChecker())
with ExitStack() as stack:
 streams={}
 for kind in ['conversations','notifications']:
  path='/api/v1/'+kind;sync=ok('GET',path+'/sync',a)
  response=spec['paths'][path+'/sync']['get']['responses']['200']['content']['application/json']['schema']
  Draft202012Validator({**response,'components':spec['components']},format_checker=FormatChecker()).validate(sync)
  assert call('GET',path+'/sync',None)[0]==401
  assert call('GET',path+'/stream',a)[0]==400
  streams[kind]=stack.enter_context(Stream(a,path+'/stream',sync['cursor']))
 message=ok('POST','/api/v1/conversations/'+cid+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Contract private body'})
 validator.validate(streams['conversations'].next('message.created',cid))
 domain=event_for(cid,message['sequence']);wait(lambda:delivered(domain),'contract notification')
 nid=sql("SELECT id FROM notification_app.notifications WHERE event_id='"+domain['eventId']+"';")
 validator.validate(streams['notifications'].next('notification.created',nid))
for path in ['/api/v1/conversations/stream','/api/v1/notifications/stream']:
 request=urllib.request.Request('http://localhost:8080'+path,headers={'Origin':'http://localhost:3000','Access-Control-Request-Method':'GET','Access-Control-Request-Headers':'authorization,last-event-id'},method='OPTIONS')
 with urllib.request.urlopen(request) as response:
  assert response.headers['Access-Control-Allow-Origin']=='http://localhost:3000'
  assert 'last-event-id' in response.headers['Access-Control-Allow-Headers'].lower()
print('PASS live OpenAPI/stream contracts, authentication, required cursor and explicit-origin CORS')

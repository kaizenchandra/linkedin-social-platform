#!/usr/bin/env python3
from mvp5_support import *
import urllib.parse

a,b,cid=pair();private='MVP5-private-trace-canary-'+uuid.uuid4().hex;path='/api/v1/conversations/'+cid
cursor=ok('GET',path.rsplit('/',1)[0]+'/sync',a)['cursor']
with Stream(a,'/api/v1/conversations/stream',cursor) as stream:
 message=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':private});stream.next('message.created',cid)
domain=event_for(cid,message['sequence']);wait(lambda:delivered(domain),'traced notification')
parent=sql("SELECT trace_parent FROM messaging_app.outbox WHERE id='"+domain['eventId']+"';")
assert parent.startswith('00-') and len(parent)==55,'This check requires the optional OpenTelemetry profile'
trace_id=parent.split('-')[1];evidence={}
def check():
 try:trace=json.load(urllib.request.urlopen('http://localhost:9411/api/v2/trace/'+trace_id,timeout=5))
 except urllib.error.HTTPError as error:
  if error.code==404:return False
  raise
 services={s.get('localEndpoint',{}).get('serviceName') for s in trace}
 if {'api-gateway','messaging-service','notification-service'}<=services and any(s.get('kind')=='CONSUMER' for s in trace):
  assert private not in json.dumps(trace);evidence['traceId']=trace_id;evidence['services']=sorted(services);evidence['matchedCommittedOutboxTraceParent']=True;return True
 return False
wait(check,'message HTTP and Kafka notification trace')
for query in ['network_stream_active','network_stream_events_persisted_total','network_stream_events_emitted_total','network_stream_replay_requests_total','network_stream_emission_lag_seconds_count','network_stream_oldest_pending_seconds']:
 def scraped():
  values=json.load(urllib.request.urlopen('http://localhost:9095/api/v1/query?'+urllib.parse.urlencode({'query':query}),timeout=5))['data']['result']
  if not values:return False
  for value in values:assert not any(k in value['metric'] for k in ['member_id','conversation_id','event_id','owner_id'])
  evidence[query]=[{'labels':v['metric'],'value':v['value'][1]} for v in values];return True
 wait(scraped,'scraped stream metric '+query,45)
logs=subprocess.check_output(['docker','compose','logs','--since','5m','messaging-service','notification-service','api-gateway'],text=True)
assert private not in logs;evidence['privateBodyAbsentFromLogsAndTrace']=True
Path('docs/mvp5-telemetry-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n');print('PASS HTTP/Kafka trace continuity, low-cardinality stream metrics and no private body in logs/traces')

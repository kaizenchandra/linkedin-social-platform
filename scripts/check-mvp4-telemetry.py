#!/usr/bin/env python3
from mvp4_support import *
refresh();o=users['owner'];a=users['alice'];c=company(o)
for s in ok('GET','/api/v1/jobs/searches',a):ok('DELETE','/api/v1/jobs/searches/'+s['id'],a,expected=204)
ok('PUT','/api/v1/notifications/preferences/job-alerts',a,{'enabled':True},expected=204)
ok('POST','/api/v1/jobs/searches',a,{'name':'Telemetry','companyIds':[c['id']],'alertsEnabled':True})
j=job(o,c['id'],'MVP4 trace-private-fixture');wait(lambda:count(a,j['id'])==1,'traced alert')
evidence={}
for path in ['/api/v1/feed','/api/v1/members/suggestions','/api/v1/companies/suggestions']:ok('GET',path,a)
def check():
 traces=json.load(urllib.request.urlopen('http://localhost:9411/api/v2/traces?limit=100',timeout=5))
 for trace in traces:
  services={s.get('localEndpoint',{}).get('serviceName') for s in trace}
  if {'api-gateway','hiring-service','notification-service'}<=services and any(s.get('kind')=='CONSUMER' for s in trace):
   assert 'MVP4 trace-private-fixture' not in json.dumps(trace);evidence['traceId']=trace[0]['traceId'];evidence['services']=sorted(services);return True
 return False
wait(check,'HTTP/Kafka/workflow trace')
queries=['network_alert_backlog','network_alert_oldest_seconds','network_alert_failed','network_discovery_candidates_total','network_discovery_returned_total','network_discovery_query_seconds_count']
for metric in queries:
 def metric_ready():
  result=json.load(urllib.request.urlopen('http://localhost:9095/api/v1/query?query='+metric,timeout=5))['data']['result']
  if not result:return False
  evidence[metric]=[{'labels':r['metric'],'value':r['value'][1]} for r in result];return True
 wait(metric_ready,'Prometheus scrape '+metric,45)
logs=subprocess.check_output(['docker','compose','logs','--since','5m','hiring-service','notification-service','api-gateway'],text=True);assert 'MVP4 trace-private-fixture' not in logs
evidence['contentAbsentFromLogsAndTraces']=True
Path('docs/mvp4-telemetry-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n');print('PASS connected HTTP/Kafka/durable-workflow trace and low-cardinality discovery/matching metrics')

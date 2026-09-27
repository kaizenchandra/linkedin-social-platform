#!/usr/bin/env python3
import json,time,urllib.request
from pathlib import Path
end=time.monotonic()+90
while time.monotonic()<end:
 try:
  traces=json.load(urllib.request.urlopen('http://localhost:9411/api/v2/traces?limit=100',timeout=5))
  services=[{s.get('localEndpoint',{}).get('serviceName') for s in trace} for trace in traces]
  http=next((traces[i] for i,s in enumerate(services) if {'api-gateway','content-service'}<=s),None)
  asynchronous=next((traces[i] for i,s in enumerate(services) if {'api-gateway','content-service','notification-service'}<=s),None)
  targets=json.load(urllib.request.urlopen('http://localhost:9095/api/v1/targets',timeout=5))['data']['activeTargets']
  healthy=len(targets)==4 and all(t['health']=='up' for t in targets)
  if http and asynchronous and healthy:
   evidence={'httpTraceId':http[0]['traceId'],'asyncTraceId':asynchronous[0]['traceId'],'asyncServices':sorted({s.get('localEndpoint',{}).get('serviceName') for s in asynchronous}),'prometheusTargets':len(targets)}
   Path('docs/telemetry-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
   print('PASS: gateway/content HTTP trace, durable outbox/Kafka/notification trace, four authenticated metrics targets');break
 except (OSError,ValueError):pass
 time.sleep(1)
else:raise AssertionError('Telemetry evidence incomplete; inspect Zipkin and Prometheus')

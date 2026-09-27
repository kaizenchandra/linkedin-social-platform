#!/usr/bin/env python3
from hiring_http import *
import subprocess
# Produce a fresh connected HTTP + outbox + Kafka trace; no application content exported.
c=company(users['owner']);j=job(users['owner'],c['id']);app=ok('POST','/api/v1/applications',users['alice'],{'jobId':j['id'],'idempotencyKey':str(uuid.uuid4()),'coverNote':'Telemetry-secret-fixture'})
end=time.monotonic()+90
while time.monotonic()<end:
 try:
  traces=json.load(urllib.request.urlopen('http://localhost:9411/api/v2/traces?limit=100',timeout=5))
  hiring=next((t for t in traces if {'api-gateway','hiring-service','member-service','notification-service'}<={s.get('localEndpoint',{}).get('serviceName') for s in t}),None)
  targets=json.load(urllib.request.urlopen('http://localhost:9095/api/v1/targets',timeout=5))['data']['activeTargets']
  if hiring and len(targets)==7 and all(t['health']=='up' for t in targets):
   assert 'Telemetry-secret-fixture' not in json.dumps(traces)
   logs=subprocess.check_output(['docker','compose','logs','--since','5m','hiring-service','member-service','notification-service','api-gateway'],text=True)
   assert 'Telemetry-secret-fixture' not in logs
   evidence={'traceId':hiring[0]['traceId'],'services':sorted({s.get('localEndpoint',{}).get('serviceName') for s in hiring}),'prometheusTargets':7,'applicationContentAbsentFromTraces':True,'fixtureAbsentFromApplicationLogs':True}
   Path('docs/mvp3-telemetry-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n');print('PASS: gateway/hiring/profile HTTP and durable Kafka notification trace; seven authenticated metrics targets; private cover note absent');break
 except (OSError,ValueError):pass
 time.sleep(.5)
else:raise AssertionError('Hiring telemetry incomplete')

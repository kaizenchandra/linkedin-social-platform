#!/usr/bin/env python3
from hiring_http import *
import subprocess,sys
backend=sys.argv[1] if len(sys.argv)>1 else 'compose';a=users['alice'];o=users['owner']
def snapshot():return [ok('GET','/api/v1/companies/'+session['companyId'],o),ok('GET','/api/v1/companies/'+session['companyId']+'/jobs/'+session['jobId'],o),ok('GET','/api/v1/applications/'+session['applicationId'],a),ok('GET','/api/v1/applications/'+session['applicationId']+'/history',a)]
before=snapshot();logoBefore=download(a,session['logoId']) if 'logoId' in session else None;services=['member-service','content-service','notification-service','media-service','messaging-service','hiring-service','api-gateway']
if backend=='kind':
 for service in services:
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','restart','deployment/'+service],check=True)
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','status','deployment/'+service,'--timeout=180s'],check=True)
else:subprocess.run(['docker','compose','restart',*services],check=True)
end=time.monotonic()+150
while time.monotonic()<end:
 try:
  if snapshot()==before:break
 except (OSError,AssertionError):pass
 time.sleep(.5)
else:raise AssertionError('Persisted hiring state mismatch after restart')
if logoBefore is not None:assert download(a,session['logoId'])==logoBefore
print('PASS: '+backend+' restart preserves exact company/job/application/snapshots/status history')

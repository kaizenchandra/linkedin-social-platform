#!/usr/bin/env python3
from mvp4_support import *
import sys
refresh();backend=sys.argv[1] if len(sys.argv)>1 else 'compose';a=users['alice'];b=users['bob']
paths=['/api/v1/members/me/following?size=100','/api/v1/companies/following?size=100','/api/v1/posts/saved?size=100','/api/v1/jobs/saved?size=100','/api/v1/jobs/searches','/api/v1/notifications/preferences/job-alerts']
def snapshot():return [ok('GET',path,user) for user in [a,b] for path in paths]
before=snapshot();services=['member-service','content-service','notification-service','media-service','messaging-service','hiring-service','api-gateway']
if backend=='kind':
 for service in services:
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','restart','deployment/'+service],check=True,capture_output=True)
  subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','rollout','status','deployment/'+service,'--timeout=180s'],check=True,capture_output=True)
else:compose('restart',*services)
def same():
 try:return snapshot()==before
 except (OSError,AssertionError):return False
wait(same,'exact persisted discovery state',180)
print('PASS '+backend+' restart preserves member/company follows, saved posts/jobs/searches, versions and preferences')

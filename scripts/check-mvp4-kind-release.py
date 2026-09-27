#!/usr/bin/env python3
"""Upgrade only the dedicated retained kind cluster; restore original Compose on exit."""
import json,os,subprocess,time
from pathlib import Path
node='professional-network-mvp-control-plane';k=['scripts/kubectl-local.sh','-n','network-mvp'];compose=['docker','compose','-f','compose.yaml','-f','compose.observability.yaml'];env={**os.environ,'MVP4_BACKEND':'kind'}
services=['member-service','content-service','notification-service','media-service','messaging-service','hiring-service','api-gateway']
sessions={p:p.read_bytes() for p in [Path('.local/session.json'),Path('.local/hiring-session.json'),Path('.local/mvp4-events.json'),Path('.local/mvp4-alert-fixture.json')] if p.exists()}
def run(args,**kw):return subprocess.run(args,check=True,**kw)
def script(name,*args):run(['python3','scripts/'+name+'.py',*args],env=env)
try:
 run(compose+['stop'])
 run(['docker','start',node])
 end=time.monotonic()+180
 while time.monotonic()<end:
  if subprocess.run(['scripts/kubectl-local.sh','get','--raw=/readyz','--request-timeout=5s'],capture_output=True).returncode==0:break
  time.sleep(1)
 else:raise AssertionError('Dedicated kind API unavailable')
 run(k+['rollout','status','deployment/oracle','--timeout=300s'])
 # Stop application background work before the upgrade-state comparison.
 for service in services:run(k+['scale','deployment/'+service,'--replicas=0'])
 for service in services:run(k+['wait','--for=delete','pod','-l','app='+service,'--timeout=90s'])
 verified=Path('docs/mvp4-kind-upgrade-evidence.json').exists()
 if not verified and not Path('.local/mvp4-kind-upgrade-before.json').exists():script('check-mvp4-kind-upgrade','before')
 run(['scripts/deploy-kind.sh'])
 for manifest in ['infrastructure','migrations','applications']:run(['scripts/kubectl-local.sh','apply','--dry-run=server','-f','infra/k8s/'+manifest+'.json'])
 if not verified:script('check-mvp4-kind-upgrade','after')
 script('auth-hiring-setup');script('smoke-mvp3');script('check-mvp3-restart','kind')
 script('auth-hiring-setup');script('smoke-mvp4');script('check-mvp4-restart','kind')
 script('auth-test-setup');script('smoke')
 script('auth-test-setup');script('auth-moderator-setup');script('smoke-mvp2')
 deployments=json.loads(subprocess.check_output(k+['get','deployments','-o','json'],text=True))
 apps=[d for d in deployments['items'] if d['metadata']['name'] in services]
 assert len(apps)==7 and all(d['status'].get('readyReplicas')==1 for d in apps)
 Path('docs/mvp4-kind-release-evidence.json').write_text(json.dumps({'cluster':'professional-network-mvp','namespace':'network-mvp','serverValidatedResources':37,'readyApplications':{d['metadata']['name']:d['spec']['template']['spec']['containers'][0]['image'] for d in apps},'gatewayJourneys':['MVP-1','MVP-2','MVP-3','MVP-4'],'rollingRestartVerified':services,'existingPVCsPreserved':True,'nodeStoppedOnExit':True},indent=2)+'\n')
 print('PASS retained local kind upgrade, server validation, all gateway journeys, rolling restarts and persisted state')
finally:
 run(['docker','stop',node])
 for path,data in sessions.items():path.write_bytes(data);os.chmod(path,0o600)
 run(compose+['up','-d','--wait','--wait-timeout','240'])

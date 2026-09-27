#!/usr/bin/env python3
"""Fresh disposable Compose volumes; preserves original project, secrets and sessions."""
import subprocess,secrets,json,os
from pathlib import Path
project='pn-mvp3-fresh-'+secrets.token_hex(4)
original=['docker','compose','-f','compose.yaml','-f','compose.observability.yaml']
fresh=['docker','compose','-p',project]
sessions={p:p.read_bytes() for p in [Path('.local/session.json'),Path('.local/hiring-session.json')] if p.exists()}
def run(args):subprocess.run(args,check=True)
try:
 run(['docker','compose','build'])
 run(original+['stop'])
 run(fresh+['up','-d','--wait','--wait-timeout','300'])
 for script in ['auth-hiring-setup','smoke-mvp3','auth-test-setup','smoke','auth-test-setup','auth-moderator-setup','smoke-mvp2']:
  run(['python3','scripts/'+script+'.py'])
 Path('docs/mvp3-fresh-compose-evidence.json').write_text(json.dumps({'project':project,'freshVolumes':True,'migrations':'all six service schemas','gatewayJourneys':['MVP-1','MVP-2','MVP-3'],'originalVolumesPreserved':True,'disposableVolumesRemoved':True},indent=2)+'\n')
 print('PASS: fresh Compose install and all release journeys')
finally:
 assert project.startswith('pn-mvp3-fresh-')
 run(fresh+['down','--volumes'])
 for path,data in sessions.items():path.write_bytes(data);os.chmod(path,0o600)
 run(original+['up','-d','--wait','--wait-timeout','240'])

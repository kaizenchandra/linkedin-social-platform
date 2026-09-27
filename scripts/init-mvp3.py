#!/usr/bin/env python3
"""Add release secrets without replacing existing local credentials."""
from pathlib import Path
import os,secrets
p=Path('.env')
if not p.exists():raise SystemExit('Run scripts/init-local.py first')
s=p.read_text().rstrip();keys={line.split('=',1)[0] for line in s.splitlines() if '=' in line}
new=['HIRING_DB_PASSWORD','HIRING_RUNTIME_PASSWORD','HIRING_CLIENT_SECRET','NOTIFICATION_CLIENT_SECRET','KAFKA_HIRING_PASSWORD']
for key in new:
 if key not in keys:s+='\n'+key+'='+secrets.token_hex(24)
p.write_text(s.rstrip()+'\n');os.chmod(p,0o600)
print('MVP-3 local secrets present; existing values preserved')

import subprocess
subprocess.run(["python3","scripts/prepare-object-store.py"],check=True)

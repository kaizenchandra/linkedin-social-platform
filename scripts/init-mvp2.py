#!/usr/bin/env python3
"""Add release secrets without replacing existing local credentials."""
from pathlib import Path
import os,secrets
p=Path('.env')
if not p.exists():raise SystemExit('Run scripts/init-local.py first')
s=p.read_text().rstrip();keys={line.split('=',1)[0] for line in s.splitlines() if '=' in line}
new=['MEDIA_DB_PASSWORD','MEDIA_RUNTIME_PASSWORD','MESSAGING_DB_PASSWORD','MESSAGING_RUNTIME_PASSWORD','MEDIA_CLIENT_SECRET','MESSAGING_CLIENT_SECRET','KAFKA_MESSAGING_PASSWORD','S3_ACCESS_KEY','S3_SECRET_KEY','MEDIA_S3_ACCESS_KEY','MEDIA_S3_SECRET_KEY']
for key in new:
 if key not in keys:s+='\n'+key+'='+secrets.token_hex(24)
p.write_text(s.rstrip()+'\n');os.chmod(p,0o600)
print('MVP-2 local secrets present; existing values preserved')

import subprocess
subprocess.run(["python3","scripts/prepare-object-store.py"],check=True)

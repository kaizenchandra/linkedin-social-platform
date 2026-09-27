#!/usr/bin/env python3
"""Create untracked local-only secrets; never replace an existing environment."""
from pathlib import Path
import secrets,os
p=Path('.env')
if p.exists():raise SystemExit('.env already exists; preserved')
keys=['ORACLE_PASSWORD','KEYCLOAK_ADMIN_PASSWORD','CONTENT_CLIENT_SECRET','GRAFANA_PASSWORD','METRICS_CLIENT_SECRET','MEMBER_DB_PASSWORD','CONTENT_DB_PASSWORD','NOTIFICATION_DB_PASSWORD','MEMBER_RUNTIME_PASSWORD','CONTENT_RUNTIME_PASSWORD','NOTIFICATION_RUNTIME_PASSWORD','KAFKA_ADMIN_PASSWORD','KAFKA_MEMBER_PASSWORD','KAFKA_CONTENT_PASSWORD','KAFKA_NOTIFICATION_PASSWORD','MEDIA_DB_PASSWORD','MEDIA_RUNTIME_PASSWORD','MESSAGING_DB_PASSWORD','MESSAGING_RUNTIME_PASSWORD','MEDIA_CLIENT_SECRET','MESSAGING_CLIENT_SECRET','KAFKA_MESSAGING_PASSWORD','S3_ACCESS_KEY','S3_SECRET_KEY','MEDIA_S3_ACCESS_KEY','MEDIA_S3_SECRET_KEY']
p.write_text('\n'.join(k+'='+secrets.token_hex(24) for k in keys)+'\nLOCAL_UID='+str(os.getuid())+'\nLOCAL_GID='+str(os.getgid())+'\n')
os.chmod(p,0o600)
print('Created .env (mode 0600)')

import subprocess
subprocess.run(["python3","scripts/prepare-object-store.py"],check=True)

#!/usr/bin/env python3
"""Create untracked local-only secrets; never replace an existing environment."""
from pathlib import Path
import secrets,os
p=Path('.env')
if p.exists():raise SystemExit('.env already exists; preserved')
keys=['ORACLE_PASSWORD','KEYCLOAK_ADMIN_PASSWORD','CONTENT_CLIENT_SECRET','GRAFANA_PASSWORD','METRICS_CLIENT_SECRET','MEMBER_DB_PASSWORD','CONTENT_DB_PASSWORD','NOTIFICATION_DB_PASSWORD','MEMBER_RUNTIME_PASSWORD','CONTENT_RUNTIME_PASSWORD','NOTIFICATION_RUNTIME_PASSWORD','KAFKA_ADMIN_PASSWORD','KAFKA_MEMBER_PASSWORD','KAFKA_CONTENT_PASSWORD','KAFKA_NOTIFICATION_PASSWORD']
p.write_text('\n'.join(k+'='+secrets.token_hex(24) for k in keys)+'\nLOCAL_UID='+str(os.getuid())+'\nLOCAL_GID='+str(os.getgid())+'\n')
os.chmod(p,0o600)
print('Created .env (mode 0600)')

#!/usr/bin/env python3
"""Prepare owner-only metrics credentials for the optional Compose profile."""
import os
from pathlib import Path
env=dict(line.split('=',1) for line in Path('.env').read_text().splitlines() if line and not line.startswith('#'))
Path('.local').mkdir(exist_ok=True);Path('.local/prometheus-data').mkdir(mode=0o700,exist_ok=True)
p=Path('.local/metrics-secret');fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as f:f.write(env['METRICS_CLIENT_SECRET'])
os.chmod(p,0o600)
print('Prepared owner-only metrics secret; Prometheus uses LOCAL_UID/LOCAL_GID')

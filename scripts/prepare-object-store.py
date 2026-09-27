#!/usr/bin/env python3
"""Render private local S3 identities: application access is confined to its bucket."""
import json,os
from pathlib import Path
e=dict(x.split('=',1) for x in Path('.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
d={'identities':[{'name':'local-admin','credentials':[{'accessKey':e['S3_ACCESS_KEY'],'secretKey':e['S3_SECRET_KEY']}],'actions':['Admin','Read','Write','List','Tagging']},{'name':'media-service','credentials':[{'accessKey':e['MEDIA_S3_ACCESS_KEY'],'secretKey':e['MEDIA_S3_SECRET_KEY']}],'actions':['Read:network-media/*','Write:network-media','List:network-media']}]}
Path('.local').mkdir(exist_ok=True);p=Path('.local/s3-identities.json');fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as f:json.dump(d,f)
os.chmod(p,0o600);print('Prepared owner-only bucket-scoped S3 credentials')

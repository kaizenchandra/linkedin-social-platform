#!/usr/bin/env python3
import json,os
from pathlib import Path
s=json.loads(Path(os.environ.get('TEST_SESSION','.local/session.json')).read_text());env={'baseUrl':'http://localhost:8080'}
for u in s['users']:
 env[u['name']+'Token']=u['access_token'];env[u['name']+'Id']=u['id']
if 'moderator' in s:env['moderatorToken']=s['moderator']['access_token']
p=Path('requests/http-client.private.env.json');fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as f:json.dump({'local':env},f,indent=2)
print('Created owner-only IntelliJ local environment; choose local in the HTTP client')

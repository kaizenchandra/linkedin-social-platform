#!/usr/bin/env python3
import json,urllib.request,urllib.parse
from pathlib import Path
p=Path('.local/session.json');session=json.loads(p.read_text())
for u in session['users']:
 data=urllib.parse.urlencode({'grant_type':'refresh_token','client_id':session['client'],'refresh_token':u['refresh_token']}).encode()
 with urllib.request.urlopen('http://localhost:8180/realms/network/protocol/openid-connect/token',data,timeout=10) as r:u.update(json.load(r))
p.write_text(json.dumps(session));print('Refreshed isolated test sessions')

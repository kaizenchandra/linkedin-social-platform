#!/usr/bin/env python3
"""Local browser login with Keycloak S256 PKCE; credentials stay in the provider."""
import base64,hashlib,secrets,json,urllib.request,urllib.parse,webbrowser,os
from http.server import HTTPServer,BaseHTTPRequestHandler
from pathlib import Path
issuer='http://localhost:8180/realms/network';callback='http://localhost:8765/callback';verifier=secrets.token_urlsafe(48);state=secrets.token_urlsafe(32)
challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  q=urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
  if urllib.parse.urlparse(self.path).path!='/callback' or q.get('state')!=[state] or 'code' not in q:self.send_error(400);return
  data=urllib.parse.urlencode({'grant_type':'authorization_code','client_id':'network-web','code':q['code'][0],'redirect_uri':callback,'code_verifier':verifier}).encode()
  with urllib.request.urlopen(issuer+'/protocol/openid-connect/token',data,timeout=10) as r:tokens=json.load(r)
  Path('.local').mkdir(exist_ok=True);p=Path('.local/interactive-token.json');fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
  with os.fdopen(fd,'w') as f:json.dump(tokens,f)
  self.send_response(200);self.end_headers();self.wfile.write(b'Login complete. Tokens saved locally. You may close this tab.');self.server.complete=True
server=HTTPServer(('127.0.0.1',8765),Handler);server.timeout=300;server.complete=False
url=issuer+'/protocol/openid-connect/auth?'+urllib.parse.urlencode({'client_id':'network-web','response_type':'code','scope':'openid','redirect_uri':callback,'state':state,'code_challenge':challenge,'code_challenge_method':'S256'})
print('Opening Keycloak login/registration. Waiting up to five minutes.');webbrowser.open(url)
server.handle_request();server.server_close()
if not server.complete:raise SystemExit('No successful callback received')
print('Saved .local/interactive-token.json with owner-only permissions')

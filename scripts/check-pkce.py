#!/usr/bin/env python3
"""Exercise the actual browser authorization form with a disposable test identity and S256 PKCE."""
import urllib.request,urllib.parse,http.cookiejar,json,secrets,hashlib,base64
from html.parser import HTMLParser
from pathlib import Path
class LocalBrowserPolicy(http.cookiejar.DefaultCookiePolicy):
 # Browsers treat localhost as a secure context; urllib does not. Restrict this
 # test-only compatibility behavior to the exact loopback hostname.
 def return_ok_secure(self,cookie,request):
  if urllib.parse.urlparse(request.full_url).hostname=='localhost':return True
  return super().return_ok_secure(cookie,request)
class Callback(Exception):pass
class Redirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,req,fp,code,msg,headers,newurl):
  if newurl.startswith('http://localhost:8765/callback'):
   self.callback=newurl;raise Callback()
  return super().redirect_request(req,fp,code,msg,headers,newurl)
class Form(HTMLParser):
 action=None
 def handle_starttag(self,tag,attrs):
  attrs=dict(attrs)
  if tag=='form' and attrs.get('id')=='kc-form-login':self.action=attrs['action']
u=json.loads(Path('.local/session.json').read_text())['users'][0]
v=secrets.token_urlsafe(48);challenge=base64.urlsafe_b64encode(hashlib.sha256(v.encode()).digest()).decode().rstrip('=');state=secrets.token_hex(20)
base='http://localhost:8180/realms/network/protocol/openid-connect'
redirect=Redirect();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar(policy=LocalBrowserPolicy())),redirect)
params={'client_id':'network-web','response_type':'code','scope':'openid','redirect_uri':'http://localhost:8765/callback','state':state,'code_challenge':challenge,'code_challenge_method':'S256'}
html=opener.open(base+'/auth?'+urllib.parse.urlencode(params),timeout=10).read().decode();form=Form();form.feed(html);assert form.action,'No Keycloak login form'
try:opener.open(form.action,urllib.parse.urlencode({'username':u['username'],'password':u['password'],'credentialId':''}).encode(),timeout=10)
except Callback:pass
assert hasattr(redirect,'callback'),'Login did not produce authorization callback'
q=urllib.parse.parse_qs(urllib.parse.urlparse(redirect.callback).query);assert q['state']==[state]
data={'grant_type':'authorization_code','client_id':'network-web','code':q['code'][0],'redirect_uri':'http://localhost:8765/callback','code_verifier':v}
with urllib.request.urlopen(base+'/token',urllib.parse.urlencode(data).encode(),timeout=10) as r:token=json.load(r)
assert token['access_token'] and token['refresh_token']
with urllib.request.urlopen(base+'/token',urllib.parse.urlencode({'grant_type':'refresh_token','client_id':'network-web','refresh_token':token['refresh_token']}).encode(),timeout=10) as r:refreshed=json.load(r)
with urllib.request.urlopen(base+'/logout',urllib.parse.urlencode({'client_id':'network-web','refresh_token':refreshed['refresh_token']}).encode(),timeout=10) as r:assert r.status==204
print('PASS: Authorization Code + S256 PKCE, refresh and logout against real Keycloak')

#!/usr/bin/env python3
import json,time,urllib.request,urllib.error
from pathlib import Path
base='http://localhost:8080'
def call(method,path,token=None,data=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,None if data is None else json.dumps(data).encode(),headers,method=method),timeout=5) as r:return r.status,json.loads(r.read() or 'null')
 except urllib.error.HTTPError as e:return e.code,e.read().decode()
deadline=time.monotonic()+90
while True:
 try:
  if call('GET','/api/v1/members/me')[0]==401:break
 except (OSError,urllib.error.URLError):pass
 if time.monotonic()>deadline:raise AssertionError('Gateway readiness timeout')
 time.sleep(.5)
assert call('GET','/api/v1/members/me')[0]==401
session=json.loads(Path('.local/session.json').read_text())
for u in session['users']:
 status,body=call('PUT','/api/v1/members/me',u['access_token'],{'displayName':u['name'],'headline':'Engineer','summary':'Local acceptance test','location':'Local','experiences':[]})
 assert status==200,(status,body)
 assert body['id']==u['id']
 assert 'email' not in body
 assert call('GET','/api/v1/members/me',u['access_token'])[0]==200
print('PASS: unauthenticated 401; three real JWT profiles created/read through gateway')

#!/usr/bin/env python3
import json,urllib.request,urllib.error,http.client
from pathlib import Path
s=json.loads(Path('.local/session.json').read_text());u=s['users'][0]
for port,path in [(8080,'/api/v1/members/me'),(8081,'/api/v1/members/me'),(8082,'/api/v1/feed'),(8083,'/api/v1/notifications'),(8084,'/api/v1/media/'+str(__import__('uuid').uuid4())),(8085,'/api/v1/conversations'),(8086,'/api/v1/companies')]:
 for auth in [None,'invalid-token']:
  try:
   urllib.request.urlopen(urllib.request.Request(f'http://localhost:{port}'+path,headers={} if auth is None else {'Authorization':'Bearer '+auth}),timeout=5);raise AssertionError('Authentication bypass')
  except urllib.error.HTTPError as e:assert e.code==401
headers={'Authorization':'Bearer '+u['access_token'],'Content-Type':'application/json'}
for path,body in [('/api/v1/posts',{'body':'forged','authorId':s['users'][1]['id']}),('/api/v1/members/me',{'displayName':'Forged','experiences':[],'id':s['users'][1]['id']})]:
 try:urllib.request.urlopen(urllib.request.Request('http://localhost:8080'+path,json.dumps(body).encode(),headers,method='PUT' if path.endswith('me') else 'POST'),timeout=5);raise AssertionError('Unknown identity field accepted')
 except urllib.error.HTTPError as e:assert e.code==400,e.code
connection=http.client.HTTPConnection('localhost',8080,timeout=10)
connection.request('POST','/api/v1/posts',body=iter([b'{"body":"',b'x'*70000,b'"}']),headers=headers,encode_chunked=True)
response=connection.getresponse();assert response.status==413,response.status;response.read();connection.close()
print('PASS: independent service authentication, forged body identity rejection, bounded chunked requests')

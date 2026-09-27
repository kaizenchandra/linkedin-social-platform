"""Bounded HTTP helpers for isolated hiring acceptance fixtures; never prints tokens."""
import json,urllib.request,urllib.error,uuid,time
from pathlib import Path
session=json.loads(Path('.local/hiring-session.json').read_text())
users={u['name']:u for u in session['users']};users['moderator']=session['moderator']
base='http://localhost:8080'
def call(method,path,user,data=None):
 headers={'Content-Type':'application/json'}
 if user:headers['Authorization']='Bearer '+user['access_token']
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,None if data is None else json.dumps(data).encode(),headers,method=method),timeout=15) as r:return r.status,json.loads(r.read() or 'null')
 except urllib.error.HTTPError as e:
  body=e.read();return e.code,json.loads(body) if body else None
def ok(method,path,user,data=None,expected=200):
 status,value=call(method,path,user,data);assert status==expected,(method,path,status,value);return value
def profiles():
 for u in users.values():ok('PUT','/api/v1/members/me',u,{'displayName':u['name'],'headline':'Professional','summary':'Application-time professional summary','location':'Local','experiences':[{'company':'Previous employer','title':'Engineer','startMonth':'2020-01'}]})
def company(user):return ok('POST','/api/v1/companies',user,{'displayName':'Local company','slug':'company-'+uuid.uuid4().hex,'description':'Self-created company','industry':'Software','location':'Remote','website':'https://example.com'})
def invite(owner,company_id,recruiter):
 i=ok('POST','/api/v1/companies/'+company_id+'/invitations',owner,{'memberId':recruiter['id']});ok('POST','/api/v1/company-invitations/'+i['id']+'/accept',recruiter);return i
def upload(user):
 boundary='upload-'+uuid.uuid4().hex
 body=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="logo.png"\r\nContent-Type: image/png\r\n\r\n').encode()+Path('requests/fixtures/image.png').read_bytes()+('\r\n--'+boundary+'--\r\n').encode()
 with urllib.request.urlopen(urllib.request.Request(base+'/api/v1/media',body,{'Authorization':'Bearer '+user['access_token'],'Content-Type':'multipart/form-data; boundary='+boundary}),timeout=15) as r:return json.load(r)
def attachment(user,cid,mid):return {'operationId':str(uuid.uuid4()),'resourceType':'COMPANY','resourceId':cid,'mediaIds':[mid]}
def download(user,mid):
 try:
  with urllib.request.urlopen(urllib.request.Request(base+'/api/v1/media/'+mid+'/content',headers={'Authorization':'Bearer '+user['access_token']}),timeout=15) as r:return r.status,r.read()
 except urllib.error.HTTPError as e:return e.code,e.read()
def save():Path('.local/hiring-session.json').write_text(json.dumps(session))
def job(user,cid,title='Professional engineer'):
 body={'title':title,'description':'Application-time job description','location':'London','workArrangement':'REMOTE','employmentType':'FULL_TIME'}
 j=ok('POST','/api/v1/companies/'+cid+'/jobs',user,body);return ok('POST','/api/v1/jobs/'+j['id']+'/publish',user)

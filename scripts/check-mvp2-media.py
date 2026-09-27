#!/usr/bin/env python3
"""Image lifecycle and authoritative privacy through actual services and private S3."""
exec(open('scripts/check-connections.py').read())
import struct,zlib,uuid

def png(width=2,height=2):
 def chunk(t,d):return struct.pack('!I',len(d))+t+d+struct.pack('!I',zlib.crc32(t+d)&0xffffffff)
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!IIBBBBB',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\x00'+b'\xff\x00\x00'*width)*height))+chunk(b'IEND',b'')
def upload(user,data):
 boundary='network-'+uuid.uuid4().hex
 body=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="untrusted.txt"\r\nContent-Type: text/plain\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode()
 try:
  with urllib.request.urlopen(urllib.request.Request(base+'/api/v1/media',body,{'Authorization':'Bearer '+user['access_token'],'Content-Type':'multipart/form-data; boundary='+boundary},method='POST'),timeout=20) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,e.read().decode()
def download(user,id):
 try:
  with urllib.request.urlopen(urllib.request.Request(base+'/api/v1/media/'+id+'/content',headers={'Authorization':'Bearer '+user['access_token']}),timeout=10) as r:return r.status,r.read()
 except urllib.error.HTTPError as e:return e.code,e.read()
def attach(user,kind,resource,ids,op=None):return call('POST','/api/v1/media/attachments',user['access_token'],{'operationId':op or str(uuid.uuid4()),'resourceType':kind,'resourceId':resource,'mediaIds':ids})
assert upload(a,b'invalid bytes')[0]==400
assert upload(a,b'x'*(5*1024*1024+1))[0]==413
status,media=upload(a,png()+b'private metadata');assert status==200,(status,media)
mid=media['id'];assert media['state']=='READY';assert download(a,mid)[0]==404
status,post=call('POST','/api/v1/posts',a['access_token'],{'body':'Private image post','visibility':'CONNECTIONS'});assert status==200,(status,post)
pid=post['id'];assert attach(b,'POST',pid,[mid])[0]==404
status,other=upload(b,png());assert status==200
assert attach(a,'POST',pid,[other['id']])[0]==404
op=str(uuid.uuid4());status,result=attach(a,'POST',pid,[mid],op);assert status==200,(status,result)
assert attach(a,'POST',pid,[mid],op)[0]==200
assert call('GET','/api/v1/posts/'+pid,b['access_token'])[1]['post']['mediaIds']==[mid]
assert download(b,mid)[0]==200
assert b'private metadata' not in download(b,mid)[1]
assert download(c,mid)[0]==404
status,avatar=upload(a,png());assert status==200
assert attach(a,'PROFILE',a['id'],[avatar['id']])[0]==200
assert call('GET','/api/v1/members/'+a['id'],b['access_token'])[1]['avatarMediaId']==avatar['id']
assert download(c,avatar['id'])[0]==200
assert call('PUT','/api/v1/blocks/'+b['id'],a['access_token'])[0]==204
assert download(b,mid)[0]==404
assert download(b,avatar['id'])[0]==404
assert call('DELETE','/api/v1/blocks/'+b['id'],a['access_token'])[0]==204
assert download(b,mid)[0]==404
assert attach(a,'PROFILE',a['id'],[])[0]==200
assert download(c,avatar['id'])[0]==404
assert attach(a,'POST',pid,[])[0]==200
assert download(a,mid)[0]==404
try:
 urllib.request.urlopen('http://localhost:8333/network-media',timeout=5);raise AssertionError('Anonymous S3 list allowed')
except urllib.error.HTTPError as e:assert e.code==403,e.code
print('PASS: validated/private uploads; pending download denied; owned idempotent attachments; live visibility/blocking/avatar removal; anonymous S3 denied')

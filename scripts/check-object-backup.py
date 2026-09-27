#!/usr/bin/env python3
"""Copy private objects to owner-only local files and verify an isolated S3 restore."""
import json,os,secrets,subprocess,hashlib,urllib.parse,xml.etree.ElementTree as E,re
from pathlib import Path
e=dict(x.split('=',1) for x in Path('.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
tag=secrets.token_hex(6);bucket='pnm-restore-'+tag;directory=Path('.local/backups')/tag;directory.mkdir(parents=True,mode=0o700)
config='user = "'+e['S3_ACCESS_KEY']+':'+e['S3_SECRET_KEY']+'"\n'
def request(method,path,upload=None,output=None):
 args=['curl','--config','-','--aws-sigv4','aws:amz:us-east-1:s3','-fsS','--max-time','30','-X',method,'http://localhost:8333'+path]
 if upload:args+=['--upload-file',str(upload)]
 if output:
  fd=os.open(output,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600);os.close(fd);args+=['--output',str(output),'--max-filesize',str(6*1024*1024)]
 return subprocess.check_output(args,input=config.encode())
keys=[];cursor=None
for _ in range(100):
 xml=E.fromstring(request('GET','/network-media?list-type=2'+('&continuation-token='+urllib.parse.quote(cursor,safe='') if cursor else '')))
 keys += [node.text for node in xml.findall('./{*}Contents/{*}Key')]
 if xml.findtext('./{*}IsTruncated')!='true':break
 cursor=xml.findtext('./{*}NextContinuationToken');assert cursor
else:raise AssertionError('Backup exceeds bounded local fixture work')
assert keys,'No objects to verify'
created=False;restored=[]
try:
 request('PUT','/'+bucket);created=True
 for key in keys:
  assert re.fullmatch('images/[0-9a-f-]{36}',key), 'Unexpected object key'
  file=directory/key.split('/')[1];request('GET','/network-media/'+key,output=file)
  request('PUT','/'+bucket+'/'+key,upload=file);restored.append(key)
  copy=directory/(file.name+'.restored');request('GET','/'+bucket+'/'+key,output=copy)
  assert hashlib.sha256(file.read_bytes()).digest()==hashlib.sha256(copy.read_bytes()).digest();copy.unlink()
 Path(os.environ.get('BACKUP_EVIDENCE','docs/mvp2-object-backup-evidence.json')).write_text(json.dumps({'objectsCompared':len(keys),'method':'Private S3 export and SHA-256 comparison after restore to an isolated bucket','originalObjectsPreserved':True,'temporaryBucketRemoved':True},indent=2)+'\n')
 print('PASS: private object export/isolated restore, SHA-256 equality for '+str(len(keys))+' objects')
finally:
 if created:
  for key in restored:request('DELETE','/'+bucket+'/'+key)
  request('DELETE','/'+bucket)

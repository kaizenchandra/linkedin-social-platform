#!/usr/bin/env python3
"""Use curl's built-in SigV4; never implement custom signing or print credentials."""
import os,subprocess
from pathlib import Path
e=dict(x.split('=',1) for x in Path('.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
# Supply curl configuration via stdin, so secrets are not command-line arguments.
def request(method,path):
 config='user = "'+e['MEDIA_S3_ACCESS_KEY']+':'+e['MEDIA_S3_SECRET_KEY']+'"\n'
 return subprocess.check_output(['curl','--config','-','--aws-sigv4','aws:amz:us-east-1:s3','-sS','-o','/dev/null','-w','%{http_code}','-X',method,'http://localhost:8333'+path],input=config,text=True)
assert request('GET','/network-media?list-type=2')=='200'
assert request('PUT','/unauthorized-bucket')=='403'
assert request('GET','/another-bucket/nonexistent')=='403'
print('PASS: application S3 credential can list its bucket but cannot create buckets or read another bucket')

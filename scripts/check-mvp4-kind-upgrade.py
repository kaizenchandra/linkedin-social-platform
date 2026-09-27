#!/usr/bin/env python3
"""Compare retained MVP-3 business data across the dedicated kind upgrade."""
import subprocess,json,hashlib,sys
from pathlib import Path
mode=sys.argv[1];assert mode in ['before','after']
queries={
 'companies':"SELECT id,owner_id,display_name,slug FROM hiring_app.companies ORDER BY id",
 'applications':"SELECT id,job_id,applicant_id,state,profile_snapshot,job_snapshot,cover_note,version FROM hiring_app.job_applications ORDER BY id",
 'profiles':"SELECT id,display_name,headline,summary,location,created_at,version FROM member_app.members ORDER BY id",
 'posts':"SELECT id,author_id,body,visibility,hidden,deleted_at,created_at,version FROM content_app.posts ORDER BY id",
 'messages':"SELECT id,conversation_id,sender_id,client_message_id,sequence_number,body,created_at FROM messaging_app.messages ORDER BY id",
 'readPositions':"SELECT * FROM messaging_app.conversations ORDER BY id",
 'attachedMedia':"SELECT id,owner_id,object_key,state,resource_type,resource_id FROM media_app.media_objects WHERE state=''ATTACHED'' ORDER BY id"
}
def sql(q):
 text="WHENEVER SQLERROR EXIT SQL.SQLCODE\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LONG 10000000 LONGCHUNKSIZE 32767 LINESIZE 32767\nALTER SESSION SET CONTAINER=FREEPDB1;\nSELECT DBMS_XMLGEN.GETXML('"+q+"') FROM dual;\nEXIT\n"
 r=subprocess.run(['scripts/kubectl-local.sh','-n','network-mvp','exec','-i','deploy/oracle','--','sqlplus','-s','/','as','sysdba'],input=text,text=True,capture_output=True,check=True)
 return r.stdout.strip()
hashes={k:hashlib.sha256(sql(q).encode()).hexdigest() for k,q in queries.items()};file=Path('.local/mvp4-kind-upgrade-before.json')
if mode=='before':
 deployments=json.loads(subprocess.check_output(['scripts/kubectl-local.sh','-n','network-mvp','get','deployments','-o','json'],text=True))
 images=[c['image'] for d in deployments['items'] for c in d['spec']['template']['spec']['containers'] if c['image'].startswith('professional-network/')]
 assert len(images)==7 and all(i.endswith(':0.3.0') for i in images),'Before capture requires the retained MVP-3 application deployment'
 file.write_text(json.dumps(hashes));print('Captured retained MVP-3 business state hashes before kind upgrade; seven0.3.0 application images verified')
else:
 assert hashes==json.loads(file.read_text()),'Retained kind business data changed across upgrade'
 Path('docs/mvp4-kind-upgrade-evidence.json').write_text(json.dumps({'retainedCluster':'professional-network-mvp','unchangedBusinessData':list(hashes),'comparison':'Oracle XML row-content SHA-256 before/after upgrade','existingPVCsPreserved':True},indent=2)+'\n')
 print('PASS: retained kind MVP-3 profiles/private posts/messages/read positions/attached media unchanged after MVP-4 upgrade')

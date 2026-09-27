#!/usr/bin/env python3
"""Populate isolated MVP-1 schemas, migrate them forward, verify, then drop only fixtures."""
import os,subprocess,secrets,uuid,json,re
from pathlib import Path
tag=secrets.token_hex(4).upper();created=[];schemas={name:'PNM_U_'+name[0].upper()+'_'+tag for name in ['member','content','notification']};password=secrets.token_hex(24)
def sql(text):
 r=subprocess.run(['docker','compose','exec','-T','-e','ORACLE_PDB_SID=FREEPDB1','oracle','sqlplus','-s','/','as','sysdba'],input='WHENEVER SQLERROR EXIT SQL.SQLCODE\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0\n'+text+'\nEXIT\n',text=True,capture_output=True,check=True);return r.stdout.strip()
def migrate(service,target=None):
 env={**os.environ,'DB_USER':schemas[service].lower(),'DB_PASSWORD':password,'DB_RUNTIME_USER':schemas[service].lower()+'_r','MIGRATION_TARGET':target or ''}
 args=['docker','compose','run','--rm','--no-deps']
 for key in ['DB_USER','DB_PASSWORD','DB_RUNTIME_USER','MIGRATION_TARGET']:args+=['-e',key]
 r=subprocess.run(args+[service+'-migration'],env=env,text=True,capture_output=True)
 if r.returncode:raise RuntimeError('Migration fixture failed: '+r.stdout[-1500:]+r.stderr[-1500:])
try:
 for service,schema in schemas.items():
  sql(f'CREATE USER {schema} IDENTIFIED BY "{password}" QUOTA 100M ON USERS;\nGRANT CREATE SESSION,CREATE TABLE,CREATE SEQUENCE TO {schema};\nCREATE USER {schema}_R IDENTIFIED BY "{password}";\nGRANT CREATE SESSION TO {schema}_R;');created += [schema,schema+'_R'];migrate(service,'3' if service=='notification' else '4')
 a,b,p,c,l,e,n=[str(uuid.uuid4()) for _ in range(7)];m=schemas['member'];t=schemas['content'];z=schemas['notification']
 sql(f"INSERT INTO {m}.members(id,display_name,created_at,version) VALUES('{a}','Upgrade author',SYSTIMESTAMP,0);\nINSERT INTO {m}.members(id,display_name,created_at,version) VALUES('{b}','Upgrade reader',SYSTIMESTAMP,0);\nINSERT INTO {t}.posts(id,author_id,body,created_at,updated_at,version) VALUES('{p}','{a}','Preserved MVP-1 post',SYSTIMESTAMP,SYSTIMESTAMP,0);\nINSERT INTO {t}.comments(id,post_id,author_id,body,created_at) VALUES('{c}','{p}','{b}','Preserved comment',SYSTIMESTAMP);\nINSERT INTO {t}.post_likes(id,post_id,member_id,created_at) VALUES('{l}','{p}','{b}',SYSTIMESTAMP);\nINSERT INTO {z}.consumed_events(event_id,consumed_at) VALUES('{e}',SYSTIMESTAMP);\nINSERT INTO {z}.notifications(id,event_id,recipient_id,actor_id,resource_id,event_type,occurred_at) VALUES('{n}','{e}','{a}','{b}','{p}','post.liked',SYSTIMESTAMP);\nCOMMIT;")
 for service in schemas:migrate(service)
 assert sql(f"SELECT visibility||':'||hidden FROM {t}.posts WHERE id='{p}' AND DBMS_LOB.SUBSTR(body,100,1)='Preserved MVP-1 post' AND deleted_at IS NULL;")=='MEMBERS:0'
 for schema,table,count in [(m,'members',2),(t,'posts',1),(t,'comments',1),(t,'post_likes',1),(z,'notifications',1),(z,'consumed_events',1)]:assert int(sql(f'SELECT COUNT(*) FROM {schema}.{table};'))==count
 Path('docs/mvp2-upgrade-evidence.json').write_text(json.dumps({'baselineTargets':{'member':4,'content':4,'notification':3},'preserved':['profiles','post body','comment','like','notification','dedup record'],'postDefault':'MEMBERS, visible, not deleted','isolatedSchemasRemoved':True},indent=2)+'\n')
 print('PASS: populated isolated MVP-1 schemas upgraded without losing profiles/content/interactions/notifications; posts retain MEMBERS visibility')
finally:
 for schema in reversed(created):
  assert re.fullmatch('PNM_U_[MCN]_[0-9A-F]{8}(_R)?',schema);sql('DROP USER '+schema+' CASCADE;')

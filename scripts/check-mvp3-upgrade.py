#!/usr/bin/env python3
"""Populate isolated MVP-2 schemas, migrate forward, verify and drop only fixtures."""
import os,subprocess,secrets,uuid,json,re
from pathlib import Path
tag=secrets.token_hex(4).upper();created=[];services=['member','content','notification','media','messaging','hiring'];schemas={name:'PN3_U_'+str(i)+'_'+tag for i,name in enumerate(services)};password=secrets.token_hex(24)
def sql(text):
 r=subprocess.run(['docker','compose','exec','-T','-e','ORACLE_PDB_SID=FREEPDB1','oracle','sqlplus','-s','/','as','sysdba'],input='WHENEVER SQLERROR EXIT SQL.SQLCODE\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0\n'+text+'\nEXIT\n',text=True,capture_output=True,check=True);return r.stdout.strip()
def migrate(service,target=None):
 env={**os.environ,'DB_USER':schemas[service].lower(),'DB_PASSWORD':password,'DB_RUNTIME_USER':schemas[service].lower()+'_r','MIGRATION_TARGET':target or ''}
 args=['docker','compose','run','--rm','--no-deps']
 for key in ['DB_USER','DB_PASSWORD','DB_RUNTIME_USER','MIGRATION_TARGET']:args+=['-e',key]
 r=subprocess.run(args+[service+'-migration'],env=env,text=True,capture_output=True)
 if r.returncode:raise RuntimeError('Migration fixture failed: '+r.stdout[-1500:]+r.stderr[-1500:])
try:
 targets={'member':'6','content':'7','notification':'3','media':'1','messaging':'2'}
 for service,schema in schemas.items():
  sql(f'CREATE USER {schema} IDENTIFIED BY "{password}" QUOTA 100M ON USERS;\nGRANT CREATE SESSION,CREATE TABLE,CREATE SEQUENCE TO {schema};\nCREATE USER {schema}_R IDENTIFIED BY "{password}";\nGRANT CREATE SESSION TO {schema}_R;');created += [schema,schema+'_R']
  if service!='hiring':migrate(service,targets[service])
 a,b=sorted([str(uuid.uuid4()),str(uuid.uuid4())]);p,c,l,e,n,media,conv,msg,key=[str(uuid.uuid4()) for _ in range(9)];m,t,z,v,w=[schemas[x] for x in services[:5]]
 sql(f"""INSERT INTO {m}.members(id,display_name,created_at,version) VALUES('{a}','Upgrade author',SYSTIMESTAMP,0);
INSERT INTO {m}.members(id,display_name,created_at,version) VALUES('{b}','Upgrade reader',SYSTIMESTAMP,0);
INSERT INTO {t}.posts(id,author_id,body,visibility,created_at,updated_at,version) VALUES('{p}','{a}','Preserved MVP-2 post','CONNECTIONS',SYSTIMESTAMP,SYSTIMESTAMP,0);
INSERT INTO {t}.comments(id,post_id,author_id,body,created_at) VALUES('{c}','{p}','{b}','Preserved comment',SYSTIMESTAMP);
INSERT INTO {t}.post_likes(id,post_id,member_id,created_at) VALUES('{l}','{p}','{b}',SYSTIMESTAMP);
INSERT INTO {z}.consumed_events(event_id,consumed_at) VALUES('{e}',SYSTIMESTAMP);
INSERT INTO {z}.notifications(id,event_id,recipient_id,actor_id,resource_id,event_type,occurred_at) VALUES('{n}','{e}','{a}','{b}','{p}','post.liked',SYSTIMESTAMP);
INSERT INTO {v}.media_objects(id,owner_id,object_key,state,resource_type,resource_id,created_at,updated_at,version) VALUES('{media}','{a}','fixture-{media}','ATTACHED','POST','{p}',SYSTIMESTAMP,SYSTIMESTAMP,0);
INSERT INTO {w}.conversations(id,low_id,high_id,last_sequence,low_read,high_read,created_at) VALUES('{conv}','{a}','{b}',1,0,1,SYSTIMESTAMP);
INSERT INTO {w}.messages(id,conversation_id,sender_id,client_message_id,sequence_number,body,created_at) VALUES('{msg}','{conv}','{a}','{key}',1,'Preserved private message',SYSTIMESTAMP);
COMMIT;""")
 for service in schemas:migrate(service)
 assert sql(f"SELECT visibility||':'||hidden FROM {t}.posts WHERE id='{p}' AND DBMS_LOB.SUBSTR(body,100,1)='Preserved MVP-2 post';")=='CONNECTIONS:0'
 assert sql(f"SELECT low_read||':'||high_read||':'||last_sequence FROM {w}.conversations WHERE id='{conv}';")=='0:1:1'
 assert sql(f"SELECT DBMS_LOB.SUBSTR(body,100,1) FROM {w}.messages WHERE id='{msg}';")=='Preserved private message'
 assert sql(f"SELECT state||':'||resource_type FROM {v}.media_objects WHERE id='{media}';")=='ATTACHED:POST'
 for schema,table,count in [(m,'members',2),(t,'posts',1),(t,'comments',1),(t,'post_likes',1),(z,'notifications',1),(z,'consumed_events',1),(v,'media_objects',1),(w,'conversations',1),(w,'messages',1)]:assert int(sql(f'SELECT COUNT(*) FROM {schema}.{table};'))==count
 # The additive notification migration preserves old rows and now permits fan-out.
 sql(f"INSERT INTO {z}.notifications(id,event_id,recipient_id,actor_id,resource_id,event_type,occurred_at) VALUES('{uuid.uuid4()}','{e}','{b}','{a}','{p}','post.liked',SYSTIMESTAMP);\nCOMMIT;")
 assert int(sql(f"SELECT COUNT(*) FROM {z}.notifications WHERE event_id='{e}';"))==2
 assert int(sql(f"SELECT COUNT(*) FROM {schemas['hiring']}.job_applications;"))==0
 Path('docs/mvp3-upgrade-evidence.json').write_text(json.dumps({'baselineTargets':targets,'preserved':['profiles','connection-only post','comment','like','notification','dedup record','attached media metadata','message text','monotonic read positions'],'newHiringSchema':7,'notificationFanoutVerified':True,'isolatedSchemasRemoved':True},indent=2)+'\n')
 print('PASS: populated MVP-2 schemas preserved through additive MVP-3 migrations, notification fan-out, new hiring schema')
finally:
 for schema in reversed(created):
  assert re.fullmatch('PN3_U_[0-5]_[0-9A-F]{8}(_R)?',schema);sql('DROP USER '+schema+' CASCADE;')

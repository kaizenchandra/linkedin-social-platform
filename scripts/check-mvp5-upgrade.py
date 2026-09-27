#!/usr/bin/env python3
"""Populate isolated MVP-4 schemas, migrate forward, verify and drop only fixtures."""
import os,subprocess,secrets,uuid,json,re
from pathlib import Path
tag=secrets.token_hex(4).upper();created=[];services=['member','content','notification','media','messaging','hiring'];schemas={name:'PN5_U_'+str(i)+'_'+tag for i,name in enumerate(services)};password=secrets.token_hex(24)
def sql(text):
 r=subprocess.run(['docker','compose','exec','-T','-e','ORACLE_PDB_SID=FREEPDB1','oracle','sqlplus','-s','/','as','sysdba'],input='WHENEVER SQLERROR EXIT SQL.SQLCODE\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0\n'+text+'\nEXIT\n',text=True,capture_output=True,check=True);return r.stdout.strip()
def migrate(service,target=None):
 env={**os.environ,'DB_USER':schemas[service].lower(),'DB_PASSWORD':password,'DB_RUNTIME_USER':schemas[service].lower()+'_r','MIGRATION_TARGET':target or ''}
 args=['docker','compose','run','--rm','--no-deps']
 for key in ['DB_USER','DB_PASSWORD','DB_RUNTIME_USER','MIGRATION_TARGET']:args+=['-e',key]
 r=subprocess.run(args+[service+'-migration'],env=env,text=True,capture_output=True)
 if r.returncode:raise RuntimeError('Migration fixture failed: '+r.stdout[-1500:]+r.stderr[-1500:])
try:
 targets={'member':'7','content':'8','notification':'5','media':'2','messaging':'2','hiring':'11'}
 for service,schema in schemas.items():
  sql(f'CREATE USER {schema} IDENTIFIED BY "{password}" QUOTA 100M ON USERS;\nGRANT CREATE SESSION,CREATE TABLE,CREATE SEQUENCE TO {schema};\nCREATE USER {schema}_R IDENTIFIED BY "{password}";\nGRANT CREATE SESSION TO {schema}_R;');created += [schema,schema+'_R']
  migrate(service,targets[service])
 a,b=sorted([str(uuid.uuid4()),str(uuid.uuid4())]);p,c,l,e,n,media,conv,msg,key=[str(uuid.uuid4()) for _ in range(9)];m,t,z,v,w=[schemas[x] for x in services[:5]]
 sql(f"""INSERT INTO {m}.members(id,display_name,created_at,version) VALUES('{a}','Upgrade author',SYSTIMESTAMP,0);
INSERT INTO {m}.members(id,display_name,created_at,version) VALUES('{b}','Upgrade reader',SYSTIMESTAMP,0);
INSERT INTO {t}.posts(id,author_id,body,visibility,created_at,updated_at,version) VALUES('{p}','{a}','Preserved MVP-4 post','CONNECTIONS',SYSTIMESTAMP,SYSTIMESTAMP,0);
INSERT INTO {t}.comments(id,post_id,author_id,body,created_at) VALUES('{c}','{p}','{b}','Preserved comment',SYSTIMESTAMP);
INSERT INTO {t}.post_likes(id,post_id,member_id,created_at) VALUES('{l}','{p}','{b}',SYSTIMESTAMP);
INSERT INTO {z}.consumed_events(event_id,consumed_at) VALUES('{e}',SYSTIMESTAMP);
INSERT INTO {z}.notifications(id,event_id,recipient_id,actor_id,resource_id,event_type,occurred_at) VALUES('{n}','{e}','{a}','{b}','{p}','post.liked',SYSTIMESTAMP);
INSERT INTO {v}.media_objects(id,owner_id,object_key,state,resource_type,resource_id,created_at,updated_at,version) VALUES('{media}','{a}','fixture-{media}','ATTACHED','POST','{p}',SYSTIMESTAMP,SYSTIMESTAMP,0);
INSERT INTO {w}.conversations(id,low_id,high_id,last_sequence,low_read,high_read,created_at) VALUES('{conv}','{a}','{b}',1,0,1,SYSTIMESTAMP);
INSERT INTO {w}.messages(id,conversation_id,sender_id,client_message_id,sequence_number,body,created_at) VALUES('{msg}','{conv}','{a}','{key}',1,'Preserved private message',SYSTIMESTAMP);
COMMIT;""")
 h=schemas['hiring'];company,job,application,history=[str(uuid.uuid4()) for _ in range(4)]
 sql(f"""INSERT INTO {h}.companies(id,owner_id,display_name,slug,description,industry,location,created_at,updated_at) VALUES('{company}','{a}','Upgrade company','upgrade-{tag.lower()}','Description','Software','Local',SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO {h}.company_members(company_id,member_id,joined_at) VALUES('{company}','{a}',SYSTIMESTAMP);
INSERT INTO {h}.jobs(id,company_id,title,description,location,work_arrangement,employment_type,state,created_at,updated_at,published_at) VALUES('{job}','{company}','Historical job','Frozen job text','Local','REMOTE','FULL_TIME','PUBLISHED',SYSTIMESTAMP,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO {h}.job_applications(id,company_id,job_id,applicant_id,submission_key,request_json,cover_note,profile_snapshot,job_snapshot,company_name,state,created_at,updated_at,version) VALUES('{application}','{company}','{job}','{b}','{key}','{{"jobId":"{job}"}}','Private retained note','{{"displayName":"Frozen member","version":2,"experiences":[]}}','{{"title":"Historical job"}}','Upgrade company','IN_REVIEW',SYSTIMESTAMP,SYSTIMESTAMP,1);
INSERT INTO {h}.application_history(id,application_id,actor_id,from_state,to_state,occurred_at,application_version) VALUES('{history}','{application}','{a}','SUBMITTED','IN_REVIEW',SYSTIMESTAMP,1);
COMMIT;""")
 search,match=[str(uuid.uuid4()) for _ in range(2)]
 sql(f"""INSERT INTO {m}.member_follows VALUES('{a}','{b}',SYSTIMESTAMP);
INSERT INTO {t}.saved_posts VALUES('{b}','{p}',SYSTIMESTAMP);
INSERT INTO {h}.company_follows VALUES('{b}','{company}',SYSTIMESTAMP);
INSERT INTO {h}.saved_jobs VALUES('{b}','{job}',SYSTIMESTAMP);
INSERT INTO {h}.saved_searches(id,member_id,name,enabled,criteria_epoch,activation_epoch,created_at,updated_at) VALUES('{search}','{b}','Preserved alert',1,1,1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO {h}.job_publications VALUES('{job}','{company}','{a}',2,SYSTIMESTAMP,'Historical job','Frozen job text','Local','REMOTE','FULL_TIME');
INSERT INTO {h}.alert_work(job_id,last_member,state,created_at,updated_at) VALUES('{job}','{b}','DONE',SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO {h}.job_alert_matches VALUES('{match}','{job}','{b}','{search}',1,1,SYSTIMESTAMP);
INSERT INTO {z}.job_alert_preferences VALUES('{b}',0);
INSERT INTO {z}.job_alert_deliveries VALUES('{b}','{job}','{match}');
COMMIT;""")
 for service in schemas:migrate(service)
 assert sql(f"SELECT visibility||':'||hidden FROM {t}.posts WHERE id='{p}' AND DBMS_LOB.SUBSTR(body,100,1)='Preserved MVP-4 post';")=='CONNECTIONS:0'
 assert sql(f"SELECT low_read||':'||high_read||':'||last_sequence FROM {w}.conversations WHERE id='{conv}';")=='0:1:1'
 assert sql(f"SELECT DBMS_LOB.SUBSTR(body,100,1) FROM {w}.messages WHERE id='{msg}';")=='Preserved private message'
 assert sql(f"SELECT state||':'||resource_type FROM {v}.media_objects WHERE id='{media}';")=='ATTACHED:POST'
 for schema,table,count in [(m,'members',2),(t,'posts',1),(t,'comments',1),(t,'post_likes',1),(z,'notifications',1),(z,'consumed_events',1),(v,'media_objects',1),(w,'conversations',1),(w,'messages',1)]:assert int(sql(f'SELECT COUNT(*) FROM {schema}.{table};'))==count
 assert sql(f"SELECT state||':'||version FROM {h}.job_applications WHERE id='{application}';")=='IN_REVIEW:1'
 assert sql(f"SELECT DBMS_LOB.SUBSTR(cover_note,100,1) FROM {h}.job_applications WHERE id='{application}';")=='Private retained note'
 assert sql(f"SELECT JSON_VALUE(profile_snapshot,'$.displayName') FROM {h}.job_applications WHERE id='{application}';")=='Frozen member'
 assert sql(f"SELECT title||':'||state FROM {h}.jobs WHERE id='{job}';")=='Historical job:PUBLISHED'
 for schema,table in [(m,'member_follows'),(t,'saved_posts'),(h,'company_follows'),(h,'saved_jobs'),(h,'saved_searches'),(h,'job_publications'),(h,'alert_work'),(h,'job_alert_matches'),(z,'job_alert_deliveries')]:assert sql(f'SELECT COUNT(*) FROM {schema}.{table};')=='1'
 assert sql(f"SELECT low_read_version||':'||high_read_version FROM {w}.conversations WHERE id='{conv}';")=='0:0'
 assert sql(f"SELECT COUNT(*) FROM {w}.conversation_preferences WHERE conversation_id='{conv}' AND muted=0 AND archived=0 AND version=0;")=='2'
 for schema in [w,z]:
  for table in ['stream_heads','stream_events']:assert sql(f'SELECT COUNT(*) FROM {schema}.{table};')=='0'
 assert sql(f"SELECT enabled FROM {z}.job_alert_preferences WHERE member_id='{b}';")=='0'
 Path('docs/mvp5-upgrade-evidence.json').write_text(json.dumps({'baselineTargets':targets,'preserved':['profiles','restricted posts','comments','likes','notification dedup','attached media','messages/read positions','company ownership','published job','application snapshots/cover note/status/history'],'oldFollowsBookmarksSearchesMatchesPreserved':True,'streamHistoryNotBackfilled':True,'preferencesBackfilled':2,'readVersionsInitialized':True,'isolatedSchemasRemoved':True},indent=2)+'\n')
 print('PASS populated MVP4 -> MVP5 additive migrations; old data preserved, private preferences backfilled, no historical stream backfill')
finally:
 for schema in reversed(created):
  assert re.fullmatch('PN5_U_[0-5]_[0-9A-F]{8}(_R)?',schema);sql('DROP USER '+schema+' CASCADE;')

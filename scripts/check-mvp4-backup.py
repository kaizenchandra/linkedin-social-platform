#!/usr/bin/env python3
"""Data Pump backup and isolated restore check on the dedicated Compose Oracle only.
Creates six fresh PNM_R_* schemas and drops only those schemas after comparison.
The original schemas and persisted volumes are never dropped or overwritten.
"""
import subprocess,secrets,json,re,os
from pathlib import Path
prefix=['docker','compose','exec','-T','-e','ORACLE_PDB_SID=FREEPDB1','oracle']
def sql(statement):
 command='WHENEVER SQLERROR EXIT SQL.SQLCODE\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 300\n'+statement+'\nEXIT\n'
 r=subprocess.run(prefix+['sqlplus','-s','/','as','sysdba'],input=command,text=True,capture_output=True)
 if r.returncode:raise RuntimeError('Oracle backup verification SQL failed')
 return r.stdout.strip()
tag=secrets.token_hex(4).upper();dump='pnm_'+tag+'.dmp';owners=['MEMBER_APP','CONTENT_APP','NOTIFICATION_APP','MEDIA_APP','MESSAGING_APP','HIRING_APP'];restores=['PNM_R_'+str(i)+'_'+tag for i in range(6)]
scn=sql('SELECT DBMS_FLASHBACK.GET_SYSTEM_CHANGE_NUMBER FROM dual;')
args=['"/ as sysdba"','directory=DATA_PUMP_DIR','schemas='+','.join(owners),'dumpfile='+dump,'logfile=export_'+tag+'.log','flashback_scn='+scn]
export=subprocess.run(prefix+['expdp',*args],capture_output=True,text=True,timeout=180)
Path('.local/backup-export.log').write_text(export.stdout+export.stderr);assert export.returncode==0,'Data Pump export failed; inspect .local/backup-export.log'
created=[]
try:
 for restore in restores:
  sql('CREATE USER '+restore+' IDENTIFIED BY "'+secrets.token_hex(24)+'" QUOTA 100M ON USERS;\nGRANT CREATE SESSION,CREATE TABLE,CREATE SEQUENCE TO '+restore+';');created.append(restore)
 args=['"/ as sysdba"','directory=DATA_PUMP_DIR','dumpfile='+dump,'logfile=import_'+tag+'.log','exclude=GRANT,USER']+['remap_schema='+a+':'+b for a,b in zip(owners,restores)]
 imported=subprocess.run(prefix+['impdp',*args],capture_output=True,text=True,timeout=180)
 Path('.local/backup-import.log').write_text(imported.stdout+imported.stderr);assert imported.returncode==0,'Data Pump import failed; inspect .local/backup-import.log'
 counts={}
 for owner,restore in zip(owners,restores):
  tables=sql("SELECT table_name FROM all_tables WHERE owner='"+owner+"' AND table_name=UPPER(table_name) ORDER BY table_name;").splitlines()
  for table in tables:
   table=table.strip();assert re.fullmatch('[A-Z][A-Z0-9_]*',table)
   original=int(sql('SELECT COUNT(*) FROM '+owner+'.'+table+';'));copied=int(sql('SELECT COUNT(*) FROM '+restore+'.'+table+';'));assert original==copied,(owner,table,original,copied);counts[owner+'.'+table]=original
 assert int(sql('SELECT COUNT(*) FROM MEMBER_APP.members a FULL OUTER JOIN '+restores[0]+'.members b ON a.id=b.id WHERE a.id IS NULL OR b.id IS NULL OR a.display_name<>b.display_name OR DBMS_LOB.COMPARE(a.summary,b.summary)<>0;'))==0
 assert int(sql('SELECT COUNT(*) FROM CONTENT_APP.posts a FULL OUTER JOIN '+restores[1]+'.posts b ON a.id=b.id WHERE a.id IS NULL OR b.id IS NULL OR DBMS_LOB.COMPARE(a.body,b.body)<>0;'))==0
 assert int(sql('SELECT COUNT(*) FROM MESSAGING_APP.messages a FULL OUTER JOIN '+restores[4]+'.messages b ON a.id=b.id WHERE a.id IS NULL OR b.id IS NULL OR a.sequence_number<>b.sequence_number OR DBMS_LOB.COMPARE(a.body,b.body)<>0;'))==0
 assert int(sql('SELECT COUNT(*) FROM MESSAGING_APP.conversations a FULL OUTER JOIN '+restores[4]+'.conversations b ON a.id=b.id WHERE a.id IS NULL OR b.id IS NULL OR a.low_read<>b.low_read OR a.high_read<>b.high_read OR a.last_sequence<>b.last_sequence;'))==0
 assert int(sql('SELECT COUNT(*) FROM HIRING_APP.job_applications a FULL OUTER JOIN '+restores[5]+'.job_applications b ON a.id=b.id WHERE a.id IS NULL OR b.id IS NULL OR a.state<>b.state OR DBMS_LOB.COMPARE(a.profile_snapshot,b.profile_snapshot)<>0 OR DBMS_LOB.COMPARE(a.job_snapshot,b.job_snapshot)<>0 OR DBMS_LOB.COMPARE(a.cover_note,b.cover_note)<>0;'))==0
 for table,columns in [('saved_searches','id,member_id,enabled,deleted,criteria_version,criteria_epoch,activation_epoch,version'),('alert_work','job_id,last_member,state,attempts'),('job_alert_matches','id,job_id,member_id,search_id,criteria_version,activation_epoch')]:
  assert int(sql('SELECT COUNT(*) FROM (SELECT '+columns+' FROM HIRING_APP.'+table+' MINUS SELECT '+columns+' FROM '+restores[5]+'.'+table+');'))==0
 Path('docs/mvp4-backup-restore-evidence.json').write_text(json.dumps({'method':'Oracle Data Pump flashback SCN, remap to six fresh disposable schemas','tablesCompared':counts,'profileAndPostTextCompared':True,'messageTextAndReadPositionsCompared':True,'hiringSnapshotsAndStatusesCompared':True,'savedSearchVersionsAndMatchingCheckpointsCompared':True,'originalDataPreserved':True},indent=2)+'\n')
 print('PASS: Data Pump export/isolated restore; all table counts and profile/post text match')
finally:
 for restore in created:
  assert re.fullmatch('PNM_R_[0-5]_[0-9A-F]{8}',restore)
  sql('DROP USER '+restore+' CASCADE;')

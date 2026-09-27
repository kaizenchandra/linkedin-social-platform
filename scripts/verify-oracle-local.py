#!/usr/bin/env python3
"""Run the full suite in disposable service-owned schemas on dedicated local Compose Oracle.
Isolation prevents live outbox/matching workers from processing test-owned rows.
"""
import os,subprocess,secrets,re
from pathlib import Path
values=dict(x.split('=',1) for x in Path('.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
env=os.environ.copy();env.update(TEST_DB_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1',S3_ACCESS_KEY=values['MEDIA_S3_ACCESS_KEY'],S3_SECRET_KEY=values['MEDIA_S3_SECRET_KEY'])
services=['member','content','notification','media','messaging','hiring'];tag=secrets.token_hex(4).upper();created=[]
def sql(statement):
 result=subprocess.run(['docker','compose','exec','-T','-e','ORACLE_PDB_SID=FREEPDB1','oracle','sqlplus','-s','/','as','sysdba'],input='WHENEVER SQLERROR EXIT SQL.SQLCODE\n'+statement+'\nEXIT\n',text=True,capture_output=True)
 if result.returncode:raise RuntimeError('Disposable Oracle test schema operation failed: '+result.stdout[-600:])
try:
 for service in services:
  schema='PN4_T_'+service.upper()+'_'+tag;password=secrets.token_hex(24)
  sql('CREATE USER '+schema+' IDENTIFIED BY "'+password+'" QUOTA 100M ON USERS;\nGRANT CREATE SESSION,CREATE TABLE,CREATE SEQUENCE TO '+schema+';');created.append(schema)
  prefix='TEST_' if service=='member' else 'TEST_'+service.upper()+'_'
  env[prefix+'DB_USER']=schema.lower();env[prefix+'DB_PASSWORD']=password
 result=subprocess.call(['scripts/java21.sh','-B','-ntp','clean','verify'],env=env)
finally:
 for schema in reversed(created):
  assert re.fullmatch('PN4_T_(MEMBER|CONTENT|NOTIFICATION|MEDIA|MESSAGING|HIRING)_[0-9A-F]{8}',schema)
  sql('DROP USER '+schema+' CASCADE;')
raise SystemExit(result)

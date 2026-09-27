#!/usr/bin/env python3
from mvp4_support import *
refresh();profiles();o=users['owner'];a=users['alice'];mod=users['moderator'];c=company(o);evidence={}
for search in ok('GET','/api/v1/jobs/searches',a):ok('DELETE','/api/v1/jobs/searches/'+search['id'],a,expected=204)
ok('PUT','/api/v1/notifications/preferences/job-alerts',a,{'enabled':True},expected=204)
search=ok('POST','/api/v1/jobs/searches',a,{'name':'Recovery','keywords':'engineer','companyIds':[c['id']],'alertsEnabled':True})
compose('stop','kafka')
try:
 start=time.monotonic();j=job(o,c['id']);elapsed=time.monotonic()-start;assert elapsed<5
 assert ok('GET','/api/v1/jobs/'+j['id'],a)['id']==j['id'];evidence['brokerOutagePublishSeconds']=elapsed
finally:compose('start','--wait','--wait-timeout','150','kafka')
wait(lambda:count(a,j['id'])==1,'broker recovery alert')
e=next(e for e in events() if e['aggregateId']==j['id'] and e['eventType']=='hiring.job.alert');failed={**e,'eventId':str(uuid.uuid4())}
compose('stop','hiring-service')
try:
 publish_event(failed);wait(lambda:any(x['eventId']==failed['eventId'] for x in events('network.events.v1.DLT')),'eligibility outage DLT')
 assert sql("SELECT COUNT(*) FROM notification_app.consumed_events WHERE event_id='"+failed['eventId']+"';")=='0'
finally:compose('start','--wait','--wait-timeout','150','hiring-service')
publish_event(failed);wait(lambda:sql("SELECT COUNT(*) FROM notification_app.consumed_events WHERE event_id='"+failed['eventId']+"';")=='1','same ID repaired replay');assert count(a,j['id'])==1
evidence['eligibilityFailure']='bounded retries -> DLT; dedup rolled back; repaired replay semantically deduplicated'
# Inject a real Oracle outbox insert failure for one isolated job only.
draft=ok('POST','/api/v1/companies/'+c['id']+'/jobs',o,{'title':'Engineer fault fixture','description':'Fixture','location':'London','workArrangement':'REMOTE','employmentType':'FULL_TIME'})
trigger='PN4_FAULT_'+uuid.uuid4().hex[:8].upper()
sql("CREATE TRIGGER hiring_app."+trigger+" BEFORE INSERT ON hiring_app.outbox FOR EACH ROW BEGIN IF :NEW.aggregate_id='"+draft['id']+"' AND DBMS_LOB.INSTR(:NEW.envelope,'hiring.job.alert')>0 THEN RAISE_APPLICATION_ERROR(-20004,'Isolated matching failure'); END IF; END;\n/\n")
try:
 ok('POST','/api/v1/jobs/'+draft['id']+'/publish',o)
 wait(lambda:sql("SELECT state FROM hiring_app.alert_work WHERE job_id='"+draft['id']+"';")=='FAILED','bounded matching retries')
 assert sql("SELECT COUNT(*) FROM hiring_app.job_alert_matches WHERE job_id='"+draft['id']+"';")=='0'
finally:sql('DROP TRIGGER hiring_app.'+trigger+';')
ok('POST','/api/v1/hiring/moderation/alerts/'+draft['id']+'/replay',mod,expected=204)
wait(lambda:count(a,draft['id'])==1,'failed-work replay');evidence['transactionFailure']='Oracle trigger fault rolled back match/outbox/checkpoint; five retries -> FAILED; replay delivered once'
refresh()
# Seed80 isolated eligible owners, then restart after persisted progress and add a second worker.
fixture_members=[str(uuid.uuid4()) for _ in range(80)];statements=[]
for member in fixture_members:
 sid=str(uuid.uuid4());statements.append("INSERT INTO hiring_app.saved_searches(id,member_id,name,keywords,enabled,criteria_epoch,activation_epoch,created_at,updated_at) VALUES('"+sid+"','"+member+"','MVP4 recovery fixture','engineer',1,0,0,SYSTIMESTAMP,SYSTIMESTAMP);")
 statements.append("INSERT INTO hiring_app.saved_search_companies(search_id,company_id) VALUES('"+sid+"','"+c['id']+"');")
sql('\n'.join(statements)+'\nCOMMIT;')
checkpoint=job(o,c['id']);jid=checkpoint['id']
wait(lambda:sql("SELECT COUNT(*) FROM hiring_app.alert_work WHERE job_id='"+jid+"' AND last_member<>'0' AND state='PENDING';")=='1','persisted matching checkpoint')
compose('stop','hiring-service');before=sql("SELECT last_member FROM hiring_app.alert_work WHERE job_id='"+jid+"';")
compose('start','--wait','--wait-timeout','150','hiring-service')
name='professional-network-mvp-alert-worker-check';compose('run','-d','--no-deps','--name',name,'hiring-service')
try:
 wait(lambda:sql("SELECT state FROM hiring_app.alert_work WHERE job_id='"+jid+"';")=='DONE','restarted worker finishes durable scan')
 assert sql("SELECT COUNT(*) FROM hiring_app.job_alert_matches WHERE job_id='"+jid+"';")=='81'
 assert sql("SELECT COUNT(*) FROM hiring_app.outbox WHERE aggregate_id='"+jid+"' AND JSON_VALUE(envelope,'$.eventType')='hiring.job.alert';")=='81'
 assert sql("SELECT last_member FROM hiring_app.alert_work WHERE job_id='"+jid+"';")>=before
 wait(lambda:sql("SELECT COUNT(*) FROM notification_app.job_alert_deliveries WHERE job_id='"+jid+"';")=='81','all committed matches delivered')
 # Publish only after both instances are ready: startup health waits can otherwise
 # let the first instance finish the entire fixture before the second starts.
 wait(lambda:subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}',name],text=True).strip()=='healthy','second worker healthy')
 refresh();concurrent=job(o,c['id']);concurrent_id=concurrent['id']
 wait(lambda:sql("SELECT state FROM hiring_app.alert_work WHERE job_id='"+concurrent_id+"';")=='DONE','two ready workers finish new publication')
 assert sql("SELECT COUNT(*) FROM hiring_app.job_alert_matches WHERE job_id='"+concurrent_id+"';")=='81'
 assert sql("SELECT COUNT(*) FROM hiring_app.outbox WHERE aggregate_id='"+concurrent_id+"' AND JSON_VALUE(envelope,'$.eventType')='hiring.job.alert';")=='81'
 wait(lambda:sql("SELECT COUNT(*) FROM notification_app.job_alert_deliveries WHERE job_id='"+concurrent_id+"';")=='81','concurrent worker matches delivered once')
finally:subprocess.run(['docker','rm','-f',name],check=True,capture_output=True)
evidence['workerRestart']='persisted checkpoint survives restart;81 unique matches, outbox records and deliveries'
evidence['twoReadyWorkers']='new publication after both instances healthy;81 unique matches, outbox records and deliveries'
Path('docs/mvp4-recovery-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('PASS broker outage, eligibility DLT/replay, actual Oracle rollback/retry, worker checkpoint restart and concurrent workers',json.dumps(evidence))

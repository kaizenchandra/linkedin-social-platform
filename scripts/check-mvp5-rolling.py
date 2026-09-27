#!/usr/bin/env python3
"""Real two-pod local Kubernetes replay across termination, without sticky sessions."""
from mvp5_support import *
import re,select
k=['scripts/kubectl-local.sh','-n','network-mvp']
a,b,cid=pair();path='/api/v1/conversations/'+cid;evidence={}
for service,kind in [('messaging-service','conversations'),('notification-service','notifications')]:
 pods=json.loads(subprocess.check_output(k+['get','pods','-l','app='+service,'-o','json'],text=True))['items']
 ready=[p['metadata']['name'] for p in pods if p['status']['phase']=='Running' and any(c['type']=='Ready' and c['status']=='True' for c in p['status'].get('conditions',[]))]
 assert len(ready)==2,ready
 forwards=[];streams=[]
 try:
  cursor=ok('GET','/api/v1/'+kind+'/sync',a)['cursor']
  for pod in ready:
   forward=subprocess.Popen(k+['port-forward','pod/'+pod,'0:8080','--address=127.0.0.1'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True);forwards.append(forward)
   assert select.select([forward.stdout],[],[],15)[0],'port forward not ready'
   line=forward.stdout.readline();port_number=re.search(r'127.0.0.1:(\d+)',line).group(1)
   streams.append(Stream(a,'/api/v1/'+kind+'/stream',cursor,'http://127.0.0.1:'+port_number))
  first=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Private rolling fixture'})
  # Ignore unrelated queued notification events, and compare event IDs seen by both pods.
  if kind=='conversations':events_seen=[s.next('message.created',cid) for s in streams]
  else:
   domain=event_for(cid,first['sequence'])
   wait(lambda:sql("SELECT COUNT(*) FROM notification_app.notifications WHERE event_id='"+domain['eventId']+"';")=='1','rolling notification')
   nid=sql("SELECT id FROM notification_app.notifications WHERE event_id='"+domain['eventId']+"';");events_seen=[s.next('notification.created',nid) for s in streams]
  assert events_seen[0]['eventId']==events_seen[1]['eventId']
  after=streams[0].cursor
  subprocess.run(k+['delete','pod',ready[0],'--wait=false'],check=True,capture_output=True)
  assert streams[0].closed.wait(35),'Terminating pod did not close stream within grace period'
  message=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Committed while peer replaces'})
  with Stream(a,'/api/v1/'+kind+'/stream',after) as recovered:
   if kind=='conversations':assert recovered.next('message.created',cid)['resourceVersion']==message['sequence']
   else:
    domain=event_for(cid,message['sequence'])
    wait(lambda:sql("SELECT COUNT(*) FROM notification_app.notifications WHERE event_id='"+domain['eventId']+"';")=='1','notification after termination')
    nid=sql("SELECT id FROM notification_app.notifications WHERE event_id='"+domain['eventId']+"';");recovered.next('notification.created',nid)
  subprocess.run(k+['rollout','status','deployment/'+service,'--timeout=180s'],check=True,capture_output=True)
  evidence[service]={'readyPodsBefore':2,'sameEventOnBothPods':True,'terminatingConnectionClosed':True,'gatewayReplaysOriginalPodCursor':True,'replacementReady':True}
 finally:
  for stream in streams:stream.close()
  for process in forwards:process.terminate();process.wait(timeout=10)
 refresh()
Path('docs/mvp5-kind-rolling-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n');print('PASS two Kubernetes pods, bounded termination and gateway replay without affinity')

#!/usr/bin/env python3
"""Small reproducible two-instance stream/REST measurement; no capacity extrapolation."""
from mvp5_support import *
from contextlib import ExitStack
import platform,statistics,threading,concurrent.futures

def summary(values):
 values=sorted(values);return {'count':len(values),'p50Ms':statistics.median(values)*1000,'p95Ms':values[min(len(values)-1,int(len(values)*.95))]*1000,'maxMs':max(values)*1000}
a,b,cid=pair();path='/api/v1/conversations/'+cid;record={};latencies=[];rest=[];observed=[];started=time.time()
with peers() as peer:
 edge=peer['api-gateway']['base']
 with ExitStack() as stack:
  streams=[]
  for base in ['http://localhost:8080',edge]:
   for user in [a,b,users['owner']]:
    for kind in ['conversations','notifications']:
     cursor=ok_at(base,'GET','/api/v1/'+kind+'/sync',user)['cursor']
     streams.append((kind,user['id'],stack.enter_context(Stream(user,'/api/v1/'+kind+'/stream',cursor,base))))
  targets=[s for k,u,s in streams if k=='conversations' and u==a['id']]
  start=time.monotonic();duration=20;sent=0;deadline=start
  while time.monotonic()-start<duration:
   stamp=time.monotonic();message=ok('POST',path+'/messages',b,{'clientMessageId':str(uuid.uuid4()),'body':'Private bounded load fixture'});ack=time.monotonic();rest.append(ack-stamp);sent+=1
   for stream in targets:
    event=stream.next('message.created',cid);seen=time.monotonic();assert event['resourceVersion']==message['sequence']
    latencies.append(seen-ack)
    from datetime import datetime
    observed.append(time.time()-datetime.fromisoformat(event['occurredAt'].replace('Z','+00:00')).timestamp())
   stamp=time.monotonic();ok('GET',path,a);rest.append(time.monotonic()-stamp)
   deadline+=1
   threading.Event().wait(max(0,deadline-time.monotonic()))
  elapsed=time.monotonic()-start
  assert all(not s.closed.is_set() for _,_,s in streams),'Stream disconnected during bounded load'
  stats=json.loads('['+','.join(subprocess.check_output(['docker','stats','--no-stream','--format','{{json .}}',container('messaging-service'),peer['messaging-service']['name'],container('notification-service'),peer['notification-service']['name']],text=True).splitlines())+']')
  record={'hardware':platform.platform()+' '+subprocess.check_output(['sysctl','-n','machdep.cpu.brand_string'],text=True).strip(),'hostMemoryBytes':int(subprocess.check_output(['sysctl','-n','hw.memsize'],text=True)),'dockerResources':subprocess.check_output(['docker','info','--format','{{.NCPU}} CPUs / {{.MemTotal}} bytes'],text=True).strip(),'dataset':{'isolatedConversations':1,'newMessages':sent,'authenticatedAccounts':3},'replicas':{'messaging':2,'notification':2,'gateway':2},'concurrentConnections':12,'durationSeconds':elapsed,'messageRatePerSecond':sent/elapsed,'restLatency':summary(rest),'restAcknowledgementToObservedSecondsBoundary':'Lower bound on commit-to-observation, since commit precedes HTTP acknowledgement; sequential observation adds client scheduling delay','ackToObserved':summary(latencies),'eventCreationToObserved':summary(observed),'creationBoundary':'Upper bound on commit-to-observation on this local clock; occurredAt is assigned before commit','errors':0,'tracingConfiguration':{name:any('javaagent' in value for value in json.loads(subprocess.check_output(['docker','inspect','--format','{{json .Config.Env}}',name],text=True))) for name in [container('messaging-service'),peer['messaging-service']['name'],container('notification-service'),peer['notification-service']['name']]},'containers':[{'name':s['Name'],'cpu':s['CPUPerc'],'memory':s['MemUsage'],'pids':s['PIDs']} for s in stats]}
Path('docs/mvp5-load-evidence.json').write_text(json.dumps(record,indent=2)+'\n');print('PASS bounded two-instance measurement',json.dumps(record))

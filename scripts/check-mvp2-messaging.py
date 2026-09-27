#!/usr/bin/env python3
exec(open('scripts/check-connections.py').read())
import uuid,concurrent.futures
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 pairs=list(pool.map(lambda u:call('POST','/api/v1/conversations',u['access_token'],{'memberId':b['id'] if u is a else a['id']}),[a,b]))
assert all(x[0]==200 for x in pairs),pairs
cid=pairs[0][1]['id'];assert pairs[1][1]['id']==cid
path='/api/v1/conversations/'+cid
assert call('GET',path,c['access_token'])[0]==404
assert call('GET',path+'/messages',c['access_token'])[0]==404
assert call('POST','/api/v1/conversations',c['access_token'],{'memberId':a['id']})[0]==403
key=str(uuid.uuid4());body={'clientMessageId':key,'body':'Private message, never in notifications'}
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:call('POST',path+'/messages',a['access_token'],body),range(2)))
assert all(x[0]==200 for x in results),results
message=results[0][1];assert results[1][1]['id']==message['id']
assert call('POST',path+'/messages',a['access_token'],{**body,'body':'different'})[0]==409
status,second=call('POST',path+'/messages',a['access_token'],{'clientMessageId':str(uuid.uuid4()),'body':'Second message'});assert status==200,(status,second)
assert call('GET',path,b['access_token'])[1]['unreadCount']==2
assert call('PUT',path+'/read',b['access_token'],{'messageId':second['id']})[1]['readPosition']==2
assert call('PUT',path+'/read',b['access_token'],{'messageId':message['id']})[1]['readPosition']==2
assert call('GET',path,b['access_token'])[1]['unreadCount']==0
assert call('PUT',path+'/read',b['access_token'],{'messageId':str(uuid.uuid4())})[0]==404
history=call('GET',path+'/messages?size=1',b['access_token'])[1]
assert history['items'][0]['id']==message['id']
assert call('GET',path+'/messages?cursor='+history['nextCursor'],b['access_token'])[1]['items'][0]['id']==second['id']
assert call('PUT','/api/v1/blocks/'+b['id'],a['access_token'])[0]==204
assert call('POST',path+'/messages',a['access_token'],{'clientMessageId':str(uuid.uuid4()),'body':'Blocked'})[0]==403
assert len(call('GET',path+'/messages',b['access_token'])[1]['items'])==2
assert call('POST',path+'/messages',a['access_token'],body)[1]['id']==message['id']
assert call('DELETE','/api/v1/blocks/'+b['id'],a['access_token'])[0]==204
assert call('POST',path+'/messages',b['access_token'],{'clientMessageId':str(uuid.uuid4()),'body':'Disconnected'})[0]==403
end=time.monotonic()+45
while time.monotonic()<end:
 notifications=call('GET','/api/v1/notifications?size=100',b['access_token'])[1]
 notes=[n for n in notifications if n['eventType']=='message.sent' and n['resourceId']==cid]
 if len(notes)==2:break
 time.sleep(.25)
else:raise AssertionError('Generic message notifications missing')
assert all('body' not in n and 'Private message' not in json.dumps(n) for n in notes)
session['conversationId']=cid;session['messageId']=second['id'];Path('.local/session.json').write_text(json.dumps(session))
print('PASS: real concurrent conversation/send idempotency, isolation, ordered polling, monotonic reads, block/disconnection rejection, retained history and generic notifications')

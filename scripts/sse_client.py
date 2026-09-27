"""Bounded fetch-equivalent SSE verifier. Never prints bearer tokens or private payloads."""
import json,queue,threading,urllib.request,socket,time
class Stream:
 def __init__(self,user,path,cursor,base='http://localhost:8080'):
  self.events=queue.Queue(maxsize=1000);self.error=None;self.closed=threading.Event();self.cursor=cursor
  request=urllib.request.Request(base+path,headers={'Authorization':'Bearer '+user['access_token'],'Last-Event-ID':cursor})
  self.response=urllib.request.urlopen(request,timeout=15)
  assert self.response.headers['Content-Type'].startswith('text/event-stream')
  self.thread=threading.Thread(target=self.read,daemon=True);self.thread.start()
 def read(self):
  frame={}
  try:
   for raw in self.response:
    line=raw.decode().rstrip('\r\n')
    if not line:
     if 'data' in frame:
      event=json.loads(frame['data']);assert event['cursor']==frame['id'];self.cursor=event['cursor'];self.events.put_nowait(event)
     frame={}
    elif ':' in line:
     key,value=line.split(':',1)
     if key:frame[key]=value.lstrip()
  except Exception as e:
   if not self.closed.is_set():self.error=type(e).__name__
  finally:self.closed.set()
 def next(self,kind=None,resource=None,timeout=20):
  end=time.monotonic()+timeout
  while time.monotonic()<end:
   try:event=self.events.get(timeout=min(.25,max(.001,end-time.monotonic())))
   except queue.Empty:
    if self.closed.is_set():raise AssertionError('Stream closed: '+str(self.error))
    continue
   if (kind is None or event['eventType']==kind) and (resource is None or event['resourceId']==resource):return event
  raise AssertionError('Expected stream event timed out')
 def close(self):
  self.closed.set()
  try:self.response.fp.raw._sock.shutdown(socket.SHUT_RDWR)
  except (AttributeError,OSError):pass
  self.response.close();self.thread.join(timeout=2)
 def __enter__(self):return self
 def __exit__(self,*args):self.close()

const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const nodes=new Map();const document={querySelector(selector){if(!nodes.has(selector))nodes.set(selector,{textContent:'',value:''});return nodes.get(selector);}};
let requests=0,currentVersion=6;
const context={document,AbortController,TextDecoder,console:{log(){throw Error('Client must not log tokens');}},setTimeout,clearTimeout,atob,fetch:async(url,options)=>{
 requests++;assert(!url.includes('memory-only-token'));assert.equal(options.headers.Authorization,'Bearer memory-only-token');
 const body=url.endsWith('/sync')?{cursor:'boundary',unreadCount:3,state:{items:[],nextCursor:null}}:{id:'conversation',readVersion:currentVersion};
 return {ok:true,json:async()=>body};
}};
vm.runInNewContext(fs.readFileSync('tools/realtime/client.js','utf8')+'\nglobalThis.test={event,sync,streams,clear,accountKey,setToken(value){token=value;}};',context);
(async()=>{
 const test=context.test;assert.notEqual(test.accountKey({iss:'issuer-a',sub:'same',exp:1}),test.accountKey({iss:'issuer-b',sub:'same',exp:1}));assert.throws(()=>test.accountKey({sub:'same',exp:1}),/Required/);test.setToken('memory-only-token');const state={cursor:null,seen:new Set(),versions:new Map(),unread:0};test.streams.set('conversations',state);
 await test.sync('conversations',state,new AbortController().signal);assert.equal(state.cursor,'boundary');assert.equal(state.unread,3);
 function frame(id,cursor,version){return 'id: '+cursor+'\nevent: conversation.read\ndata: '+JSON.stringify({eventId:id,eventType:'conversation.read',schemaVersion:1,resourceId:'conversation',resourceVersion:version,cursor});}
 await test.event('conversations',state,frame('event-1','cursor-1',6));assert.equal(state.versions.get('conversation'),6);const before=requests;
 await test.event('conversations',state,frame('event-1','cursor-1',6));assert.equal(requests,before,'Duplicate invalidation should not reapply state');
 currentVersion=4;await test.event('conversations',state,frame('older-event','cursor-2',4));assert.equal(state.versions.get('conversation'),6,'Read version cannot move backward');assert.equal(state.unread,3,'Counts are fetched rather than arithmetically replayed');
 await assert.rejects(()=>test.event('conversations',state,'id: x\ndata: {"schemaVersion":2}'),/Unsupported/);
 await test.sync('conversations',state,new AbortController().signal);assert.equal(state.cursor,'boundary');assert.equal(state.seen.size,0);
 test.clear();assert.equal(test.streams.size,0);assert.equal(nodes.get('#events').textContent,'');
 console.log('PASS fetch-client deduplication, stale read-version rejection, authoritative counts, reset synchronization and account-state clearing; no token persistence or URL credentials');
})().catch(error=>{console.error(error.message);process.exitCode=1;});

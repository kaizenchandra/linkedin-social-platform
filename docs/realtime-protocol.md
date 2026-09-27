# MVP-5 replay protocol

Messaging and notifications have separate owner-scoped streams. REST remains authoritative; SSE carries invalidations,
never message bodies, tokens or target previews. No recipient read receipts and no total ordering across services.

## Commit order and snapshots

Each service owns `stream_heads` and `stream_events`. A transaction locks all affected owner heads in sorted UUID order,
allocates consecutive owner positions, and inserts events in the same transaction as the business change. The row lock
remains held until commit. Another transaction cannot allocate a later position for that owner before the earlier
transaction commits or rolls back. Database sequences and Kafka offsets are not browser cursors.

`GET /api/v1/{conversations|notifications}/sync` captures a durable boundary **before** fetching current state. It
returns a bounded first page, authoritative unread count and cursor. This is a conservative boundary, not an atomic
frozen snapshot. Start the stream from that cursor, finish authoritative REST pagination, then reconcile invalidated
resources. State committed during snapshot reads can appear both in the snapshot and replay; merge by
identifiers/versions and refetch authoritative counts, never apply arithmetic unread deltas. New resources after the
boundary have creation events. No snapshot-to-subscription gap is silently skipped.

`GET /api/v1/{conversations|notifications}/stream` requires `Last-Event-ID`, obtained from synchronization or a previous
event. Cursors encode protocol version, stream kind, authenticated owner and numeric position. They are continuation
state, not credentials. Malformed/foreign/unsupported cursors return 400; future cursors return 409; a cursor below the
retained floor returns 410 with reset instructions. Re-synchronize after 410. Once headers are committed, a reset/error
closes the stream; reconnect validates again. Do not advance a cursor on heartbeats.

Replay entries have `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `resourceId`, `resourceVersion` and `cursor`.
SSE `id` is the cursor; deduplicate by eventId. Events can be replayed more than once. Message/read/preference resource
versions describe only that state; clients do not infer a total business order across resources.

## Retention and multiple instances

Default retention is 24 hours, configurable. Cleanup removes only an expired contiguous prefix under the owner lock and
advances a durable floor in the same transaction. Polling locks the head during validation and bounded event retrieval
so pruning cannot silently remove a validated page. Business messages and notifications are not deleted by stream
cleanup.

Instances independently poll Oracle for their active connections. No Kafka broadcast or sticky session is required;
losing an instance loses only transport state. Kafka still generates notifications through the existing
outbox/deduplication path. Missing wake-ups cannot lose events because no wake-up is required.

## Transport and authentication

Use the existing MVC servlet stack with Servlet asynchronous nonblocking writes; no WebFlux server in business services.
Admission: 32 connections per service instance, 3 per owner; gateway 64 total and 6 per owner, all instance-local. A
connection has at most one bounded replay batch (50 events / 32 KiB), one outstanding database poll, a bounded executor
queue, 1-second idle polling (100 ms catch-up for full batches), a 5,000-event reconnect budget, 10-second heartbeat,
5-second pending-write deadline and maximum 120-second lifetime. Token expiry closes earlier. Fresh authorization is
required on reconnect. JWT revocation is not immediate; an already issued valid token can reconnect until expiry,
subject to current resource authorization.

Gateway stream routes alone override the ordinary 5-second response timeout; buffering/caching/compression are disabled
for SSE. Client-facing SSE sockets use a five-second Netty write deadline, 64 KiB requested send buffer and 16/32 KiB
write watermarks, and close rather than returning to HTTP keep-alive. Kernel buffers may adjust the request;
intermediate proxies must have their own bounded streaming configuration. Ordinary API protections remain. Stop
admitting streams on shutdown, close within the existing 25-second grace period, and reconnect elsewhere. Database
failures close streams; readiness continues to include database health.

## Read state and controls

Read commands explicitly advance a validated conversation position, never backward; only a real change increments its
read version and emits an owner-only read invalidation. Emission, socket writes and metadata reads never mark messages
read. Notification mark-read is similarly idempotent with owner-only events and authoritative unread counts.

Mute/archive are private per participant. A successful new send unarchives both sender and recipient transactionally,
without changing read state or mute. Retried sends have no new side effects. Archive filters the default list;
`archived=true` retrieves archived conversations and synchronization includes both. Message notification creation checks
the recipient's current mute state through a scoped messaging API. Failure retries/DLT instead of assuming permission.
The successful remote eligibility decision is the cancellation boundary: a later mute cannot atomically recall
in-progress or delivered notifications. Blocking still denies new sends while preserving participant history.

## Client, metrics and recovery

Serve `tools/realtime` on localhost3000 with `python3 -m http.server 3000 --directory tools/realtime`. Paste a fresh
access token in memory; the client uses fetch Authorization and Last-Event-ID headers, never URL tokens or browser
persistence. Switching subject/issuer clears both stream cursors. Refresh with the existing PKCE flow, then reconnect;
401/403 stops automatic retries. Independent streams back off with jitter up to30s, deduplicate event IDs, reject stale
read versions and refetch counts. Reset410 requires fresh synchronization. The development client bounds its state
reconciliation to2,000 conversations and reports an explicit error beyond that limit; production clients must implement
larger paged state stores.

Metrics distinguish committed event records, attempted socket emission, active buffered-event age and explicit read
commands. `oldest.pending.seconds` covers active transport batches only: there is no durable browser acknowledgement, so
global oldest-undelivered event cannot truthfully be measured. Offline replay history is not a delivery backlog.
Automated clients separately measure observation latency. Event creation-to-emission includes pre-commit time and is not
confirmed delivery. Labels are service, bounded event/disconnect categories; never owners or resource IDs.

Each instance has two polling threads and a32-task queue; JDBC queries3s, owner lock wait2s, Oracle socket read5s and
stalled poll termination5s. Retention sweeps process at most25owners and500events per owner. The head row is retained
after history cleanup to preserve cursor ownership/floor and ordering. Cleanup retries subsequent sweeps; no
message/notification retention policy is implied.

The local client is a single-tab verifier. Browser HTTP/1.1 per-origin connection limits can constrain multiple tabs
independently of server admission; a future production client should coordinate tabs or use a verified HTTP/2 edge. This
release does not claim unlimited browser connections.

Paste this into the same IntelliJ Codex project. MVP-5 adds **live messaging and notifications, reliable reconnection,
multi-device read synchronization, and operational hardening**.

You are my principal backend engineer and implementation agent working inside IntelliJ.

Extend the existing professional networking platform from MVP-4 to MVP-5 through incremental, verified development.

Implement actual code, migrations, tests, API contracts, deployment updates, and operational documentation. Do not stop
after producing a proposal or scaffolding.

## 1. Starting point and execution rules

The intended existing platform uses:

- Java 21, Spring Boot 4.x, Spring Cloud, Spring Security, and Maven.
- Gateway, member, content, media, messaging, notification, and hiring services.
- Keycloak for OIDC.
- Oracle with service-owned schemas.
- Kafka with transactional outbox publishing and idempotent consumers.
- Private S3-compatible object storage.
- Docker Compose and local Kubernetes.
- Prometheus, Grafana, OpenTelemetry, and Zipkin.

Previous releases should provide:

- Profiles, connections, follows, posts, comments, and likes.
- Visibility, blocking, images, saved items, and discovery.
- Private messaging through REST and polling.
- Companies, jobs, applications, and moderation.
- Saved searches and in-app job alerts.

Treat this as intended context, not evidence of implementation.

Before editing:

- Read applicable AGENTS.md instructions.
- Inspect the repository, API contracts, migrations, ADRs, and progress records.
- Run available baseline checks.
- Identify missing prerequisites and existing failures.
- Repair prerequisites needed by MVP-5.
- Preserve unrelated work.

Continue automatically after phase verification gates pass. Ask questions only for genuine blockers.

Do not:

- Rewrite working services unnecessarily.
- Perform unrelated framework upgrades.
- Disable security or tests to obtain passing results.
- Claim checks or deployments succeeded without evidence.
- Deploy to paid or shared infrastructure without authorization.

## 2. MVP-5 objective

Make existing messaging and notification workflows feel live while preserving durable, recoverable behavior.

Deliver:

1. Live message updates.
2. Live notification updates.
3. Reconnection and replay.
4. Consistent read state across devices.
5. Conversation muting and archiving.
6. Bounded connections, backpressure, and deployment resilience.

Keep existing REST APIs and polling available.

Exclude:

- Group conversations.
- Voice/video calls.
- Typing indicators and online presence.
- Message editing, deletion, and attachments.
- Push notifications, email, and SMS.
- End-to-end encryption.
- Account/device administration.
- A production frontend.
- New analytics, AI, or recommendation systems.

Use Server-Sent Events for server-to-client updates and REST for client commands.

Do not add WebSockets or a separate real-time service unless repository evidence establishes a concrete requirement that
SSE cannot satisfy. Document any departure before implementation.

## 3. Architecture and ownership

Extend existing services:

`messaging-service`

- Durable messages.
- Conversation read positions.
- Per-member conversation preferences.
- Authorized messaging event stream.
- Durable replay metadata.

`notification-service`

- Durable notifications.
- Notification read state.
- Authorized notification event stream.
- Durable replay metadata.

`api-gateway`

- Authenticated stream routing.
- Compatible streaming timeouts and buffering configuration.
- Connection admission controls.
- No ownership of business events or read state.

`member-service`

- Remains authoritative for connections and blocking.
- Existing send-message authorization continues to apply.

Do not move message persistence into the gateway.

Do not use an in-memory event publisher as the only delivery mechanism.

Keep database ownership and service authorization intact.

## 4. Live messaging

Support a member-scoped messaging stream that signals:

- A newly committed message in one of my conversations.
- A change to my read position.
- A change to my conversation mute/archive preferences.

Use the stream to synchronize the acting member’s devices.

Keep REST as the authoritative interface for:

- Sending messages.
- Reading conversation history.
- Updating read positions.
- Changing conversation preferences.

Do not add recipient read receipts in this release. A read-position event synchronizes the reader’s own devices only.

Event payloads:

- Include an event ID, event type, schema version, occurrence time, and relevant resource identifiers.
- Include resource versions when needed to reject stale updates.
- Prefer minimal invalidation payloads.
- Do not include message bodies, tokens, or profile snapshots.
- Clients fetch current authorized resources through REST.

An open connection does not prove delivery or reading.

Do not mark a message read when:

- It is emitted to a stream.
- A socket write succeeds.
- A client reconnects.
- A client fetches conversation metadata.

Reading remains an explicit authenticated command.

## 5. Live notifications

Provide a separate member-scoped notification stream.

Support events for:

- Notification creation.
- Notification read-state changes.

Continue existing notification generation, authorization, and deduplication behavior.

Rules:

- Only the notification owner receives its events.
- Use minimal identifiers rather than stale private previews.
- Opening a target must recheck its current visibility and authorization.
- A hidden post, inaccessible job, or revoked company role must not be bypassed through event replay.

Streaming must not bypass:

- Existing job-alert preferences.
- Conversation mute rules.
- Notification deduplication.
- Current target-access restrictions.

Keep messaging and notification streams independently recoverable. Do not imply a total order across both services.

## 6. Authentication and privacy

Authenticate every stream through the gateway and again in its owning service.

Derive the stream owner from the authenticated principal. Do not trust a client-supplied member ID.

For the local browser example:

- Use a fetch-based SSE client with an Authorization header.
- Do not put bearer tokens in URLs.
- Do not store tokens in source control or example files.
- Document how the client refreshes authentication and reconnects.

Stream lifetime:

- Bound each connection lifetime.
- End the stream at token expiry or earlier.
- Require fresh authorization on reconnect.
- Do not claim immediate revocation of already-issued JWTs unless implemented and verified.
- Document the actual revocation window.

Preserve existing blocking semantics:

- Blocking prevents new sends.
- Historical messages remain accessible to their participants under the existing policy.
- Streaming must neither broaden nor silently change that policy.

Do not log tokens, message content, or event payloads containing private data.

## 7. Durable replay and reconnect protocol

Define the replay protocol before coding.

Support:

- An event cursor returned in each replayable event.
- Reconnection with a last-seen cursor.
- At-least-once delivery.
- Client deduplication by event ID.
- Finite replay retention.
- A defined recovery path for expired cursors.

The durable source must live in the service database or an equally durable mechanism with demonstrated recovery
semantics.

Requirements:

- Persist replayable state consistently with the business change it represents.
- Do not announce a message that later rolls back.
- Do not rely on Kafka consumer offsets as browser cursors.
- Do not use timestamps alone as a lossless cursor.
- Do not assume database sequence allocation order equals transaction commit order.
- Explain how the chosen ordering prevents late commits from falling behind an acknowledged cursor.

Cursor rules:

- Scope cursors to the authenticated member and stream.
- Reject malformed, foreign, or unsupported cursors.
- Treat cursors as continuation state, not authorization.
- Bound page sizes and replay work.
- Define behavior for a cursor ahead of available data.

Snapshot and reconnect:

- Provide a REST synchronization response or equivalent protocol that pairs authoritative state with a safe replay
  boundary.
- Prevent a gap between snapshot retrieval and stream subscription.
- Require clients to merge snapshots and replayed events idempotently.
- If the cursor is expired, return a documented reset requirement.
- The client must resynchronize authoritative state before resuming live updates.

Do not silently skip missing history and pretend recovery was complete.

Start with configurable replay retention of 24 hours. This is stream-history retention, not message or notification
retention.

## 8. Multiple instances and slow clients

Support at least two instances of messaging-service and notification-service.

Every authorized device should receive updates regardless of which instance accepts its connection.

Do not assume a shared Kafka consumer group broadcasts every event to every service instance.

Choose and document a workable design, such as:

- Durable database replay with bounded polling for active connections.
- Durable replay plus best-effort wake-up signals.
- Another verified design with equivalent recovery guarantees.

Wake-up delivery may improve latency, but losing a wake-up must not lose a durable update.

For active connections:

- Use bounded queues.
- Limit outstanding writes.
- Set write and idle timeouts.
- Send heartbeat frames without advancing the replay cursor.
- Stop serving slow clients before memory becomes unbounded.
- Allow disconnected clients to recover through replay.
- Clean up resources on cancellation, timeout, and errors.

Document connection limits and whether enforcement is instance-local or cluster-wide.

Do not describe local admission limits as global limits.

## 9. Multi-device read synchronization

Preserve the existing monotonic conversation read position.

Implement:

- Idempotent read-position updates.
- Validation that the requested position belongs to the conversation.
- Read positions that never move backward.
- Versioned read-state events sent to the reader’s devices.

Unread counts:

- Remain authoritative in messaging-service.
- Count unread messages from other participants.
- Do not increment counts for the member’s own messages.
- Do not depend solely on a client increment/decrement counter.

After reconnect or a reset:

- Fetch authoritative counts and read positions.
- Reconcile local state.
- Do not replay arithmetic updates that can double-count duplicates.

For notifications:

- Preserve idempotent mark-read operations.
- Synchronize changed read state to the owner’s devices.
- Provide an authoritative unread-count endpoint or include counts in a synchronization response.

Test overlapping read commands from two devices and out-of-order event arrival.

## 10. Conversation controls

Add per-member conversation preferences.

### Mute

Support muting and unmuting a conversation.

Muting:

- Suppresses new-message in-app notifications for that member.
- Does not stop message delivery.
- Does not remove the conversation.
- Does not mark messages read.
- Does not alter the other participant’s preferences.

Check mute eligibility before creating a notification.

Document the concurrency boundary:

- Preference changes govern newly authorized notification creation.
- Already delivered notifications remain.
- Do not promise atomic cancellation across messaging-service and notification-service.

If the authoritative preference check is unavailable, retry rather than assuming notifications are permitted.

### Archive

Support archiving and unarchiving a conversation for the acting member.

Archiving:

- Moves it out of the member’s default conversation list.
- Does not delete messages.
- Does not change the other participant’s list.
- Does not mark messages read.
- Does not mute notifications.

New incoming messages automatically unarchive the recipient’s conversation. A successful outgoing message also
unarchives the sender’s conversation.

Make these transitions transactional with message persistence where they share the same service.

Keep commands idempotent and emit preference updates to the acting member’s devices.

## 11. Gateway and deployment behavior

Configure and verify:

- `text/event-stream` responses.
- Streaming flush behavior.
- Disabled buffering on the event-stream routes.
- Appropriate gateway and proxy timeouts.
- Heartbeat frequency below relevant idle timeouts.
- Bounded connection lifetime.
- Explicit CORS origins.
- Graceful shutdown.

Do not globally disable protections or timeouts for ordinary APIs.

During a rolling restart:

- Stop admitting new streams on the terminating instance.
- Drain or close existing streams within a bounded grace period.
- Let clients reconnect to another instance.
- Recover through durable cursors.
- Do not require sticky sessions for correctness.

Separate liveness from readiness. A database outage must not be hidden behind a healthy stream that cannot make
progress.

Verify the selected servlet or reactive streaming implementation against the existing service stack. Do not mix WebFlux
and blocking JPA casually or claim unlimited concurrency.

## 12. Development and test client

Create a small local verification client using the project’s existing tooling or a minimal JavaScript page.

It must:

- Accept a user-provided access token without saving it permanently.
- Open messaging and notification streams.
- Display event IDs and types without logging secrets.
- Retain cursors only for the current authenticated account.
- Clear state when switching accounts.
- Reconnect with bounded exponential backoff and jitter.
- Stop automatic retries on authentication failure until credentials refresh.
- Handle duplicate events.
- Handle replay-reset responses.
- Fetch authoritative state when required.

This is a development tool, not a production frontend.

Also provide automated clients for integration and failure tests.

## 13. Phased implementation

### Phase 0 — Baseline and protocol design

Deliver:

- MVP-4 baseline results.
- SSE API and event contracts.
- Cursor, ordering, replay, and reset semantics.
- Authentication lifecycle.
- Multiple-instance delivery design.
- Resource limits and failure behavior.

Gate:

- Resolve critical prerequisites.
- Review concurrency and snapshot/subscription gaps before implementation.

### Phase 1 — Durable event history

Implement replay storage, transactional event creation, cursor validation, and bounded reads.

Gate:

- Rolled-back changes produce no visible events.
- Concurrent commits cannot create skipped events.
- Cursors cannot expose another member’s stream.
- Retention and reset behavior are tested.

### Phase 2 — Live messaging

Implement authenticated messaging streams, heartbeats, bounded writes, and the verification client.

Gate:

- A committed message produces an update.
- Disconnect/reconnect recovers missed events.
- Duplicate events are safe.
- Token expiry closes access.
- Message bodies are absent from stream payloads.

### Phase 3 — Live notifications and read synchronization

Implement notification streaming and multi-device read updates.

Gate:

- Two devices converge on authoritative unread state.
- Read positions never move backward.
- Notification replay cannot expose private target content.
- Existing preference and deduplication rules still hold.

### Phase 4 — Mute and archive

Implement preferences, API changes, message-triggered unarchiving, and notification suppression.

Gate:

- Muting affects only the acting member’s notifications.
- Archiving preserves history and read state.
- New messages unarchive the relevant participant’s conversation.
- Concurrent preference and message operations follow documented rules.

### Phase 5 — Failure and load testing

Test:

- Two service replicas.
- Gateway restart.
- Service restart.
- Database interruption.
- Kafka interruption where applicable.
- Lost wake-up signals.
- Slow clients.
- Expired cursors.
- Expired tokens.
- Concurrent devices.

Gate:

- No committed state is lost.
- Replay repairs missed live delivery.
- Memory and queues remain bounded.
- Existing REST APIs remain usable under stream load.

### Phase 6 — Deployment and release

Update:

- Compose and Kubernetes configuration.
- Migration execution.
- Gateway/proxy settings.
- CI.
- Dashboards.
- Runbooks.
- IntelliJ HTTP examples.
- Upgrade and rollback instructions.

Gate:

- Fresh setup works.
- Upgrade from populated MVP-4 data works.
- Deployed end-to-end tests pass.
- Rolling restart recovery is demonstrated or explicitly marked blocked.

## 14. Observability and measurements

Measure:

- Active connections.
- Connection admission rejections.
- Disconnect reasons.
- Replay requests and reset responses.
- Oldest undelivered durable event.
- Commit-to-observed-event latency.
- Slow-client disconnects.
- Replay retention cleanup.
- Notification preference-check failures.

Do not use user IDs, conversation IDs, or event IDs as metric labels.

Correlate logs and traces without storing private payloads.

Separate:

- Business persistence success.
- Stream emission.
- Client observation.
- Explicit user read state.

Do not label successful socket writes as confirmed user delivery.

Report performance with hardware, dataset, replica count, concurrent connections, event rate, duration, and measured
latency. Do not infer internet-scale capacity from local testing.

## 15. End-to-end acceptance scenario

Automate:

1. Create connected members A and B.
2. Open two authenticated devices for A and one for B.
3. Send a message from B.
4. Verify both A devices receive an update and can fetch the message.
5. Mark it read on one A device.
6. Verify the other A device converges on the same read state.
7. Disconnect one device and send additional messages.
8. Reconnect with its cursor and verify recovery without duplicate state.
9. Mute the conversation as A.
10. Verify messages continue arriving without new-message notifications for A.
11. Archive it and verify the next incoming message unarchives it.
12. Restart the instance serving one device.
13. Verify reconnection to another instance and replay.
14. Simulate a slow client and verify bounded disconnection and recovery.
15. Present a foreign cursor and verify no data disclosure.
16. Expire the token and verify the connection closes.
17. Reconnect with an expired replay cursor and complete authoritative resynchronization.
18. Block B and verify new sends fail while historical access follows existing policy.
19. Replay notification events and verify deduplication.
20. Run MVP-1 through MVP-4 regression tests.

Use real Oracle and Kafka integration tests where applicable. Mocks must not be the only evidence for durability or
multiple-instance behavior.

Use condition-based waits and isolated data.

## 16. Progress and definition of done

Maintain:

- `docs/mvp5-plan.md`
- `docs/mvp5-progress.md`
- `docs/mvp5-verification.md`
- `docs/realtime-protocol.md`

Update existing architecture, ADRs, contracts, technology decisions, and operational runbooks.

At phase boundaries:

- Summarize implemented behavior.
- Record checks and actual results.
- Separate PASS, FAIL, BLOCKED, and NOT RUN.
- Record unresolved issues and the next executable action.

If interrupted, save a precise checkpoint and resume from repository evidence.

MVP-5 is complete when:

- Messaging and notification updates work across multiple instances.
- Reconnection repairs missed events without corrupting client state.
- Authentication and authorization remain enforced throughout connection lifetimes.
- Read state synchronizes across devices.
- Mute and archive behavior matches the defined policy.
- Slow clients cannot cause unbounded resource use.
- Existing REST and polling workflows remain compatible.
- Populated MVP-4 data upgrades successfully.
- Deployment and recovery claims are supported by executed checks.

Do not claim exactly-once browser delivery, immediate JWT revocation, end-to-end encryption, production readiness, or
unlimited concurrent connections.

Start by inspecting MVP-4 and running baseline verification. Then implement MVP-5 phase by phase until the acceptance
criteria are satisfied.
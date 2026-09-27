You are my principal backend engineer and implementation agent working inside IntelliJ.

Extend the existing professional networking backend from MVP-1 to MVP-2 through incremental, verified phases.

Implement actual code, migrations, tests, deployment configuration, and documentation. Do not stop at a proposal or
scaffolding.

## 1. Starting point and execution rules

The intended MVP-1 architecture contains:

- `api-gateway`
- `member-service`
- `content-service`
- `notification-service`
- Keycloak for OIDC
- Oracle with service-owned schemas
- Kafka with transactional outbox publishing and idempotent consumers
- Docker Compose and local Kubernetes deployment
- Prometheus, Grafana, OpenTelemetry, and Zipkin

Treat this as intended context, not proof of what exists.

First:

- Read applicable AGENTS.md instructions.
- Inspect the repository, architecture decisions, progress documents, migrations, and tests.
- Establish which MVP-1 features actually work.
- Run available baseline checks.
- Record missing functionality and existing failures.
- Repair prerequisites needed by MVP-2 before building dependent features.
- Preserve unrelated work.

Use the existing Java 21, Spring Boot 4.x, Spring Cloud, Spring Security, Maven, Oracle, and Kafka stack. Verify
compatibility before introducing dependencies. Do not perform unrelated framework upgrades.

Continue between phases when verification gates pass. Ask questions only for genuine blockers. Record reasonable
assumptions instead of repeatedly seeking approval.

Never:

- Rewrite working services merely to impose a different structure.
- Add placeholder implementations for required features.
- Disable security or tests to achieve a passing build.
- Claim checks passed without executing them.
- Deploy to shared or paid infrastructure without authorization.

## 2. MVP-2 objective

Extend the existing networking experience with:

1. Profile and post images.
2. Post visibility controls and member blocking.
3. Private one-to-one text messaging.
4. Basic content reporting and moderation.

Keep the scope small enough to implement and operate reliably.

Continue using REST APIs and polling. WebSockets, GraphQL, gRPC, recommendation engines, analytics platforms, and
additional language stacks are outside this release unless an existing implementation already requires them.

## 3. Feature requirements

### A. Media uploads

Support:

- One profile avatar per member.
- Up to four images per post.
- JPEG and PNG only.
- Maximum 5 MiB per uploaded file.
- A documented decoded-image size limit to prevent decompression abuse.
- Replacing/removing an avatar.
- Attaching/removing images when editing an owned post.

Add a dedicated `media-service` because uploads, binary storage, access control, and cleanup have a distinct lifecycle.

Use:

- Private S3-compatible object storage.
- A verified compatible local implementation, such as MinIO, for development.
- Oracle for media metadata.
- Random server-generated object keys.

Choose an upload flow that can enforce size limits and validate actual bytes. A bounded multipart endpoint is acceptable
for this MVP; direct-to-storage upload is not mandatory.

Media lifecycle:

- Uploaded files start as temporary and unavailable for normal download.
- Validate file signatures and decode the image.
- Strip unnecessary metadata and normalize accepted images.
- Mark validated files ready for attachment.
- Attach only media owned by the acting member.
- Track pending attachment and completed attachment explicitly.
- Make attachment retries and compensation idempotent.
- Reconcile interrupted attachment operations.
- Clean up abandoned uploads after a configurable grace period.

Do not trust filenames or client-provided MIME types. Do not fetch arbitrary user-provided URLs.

Download authorization:

- Keep storage buckets private.
- Authorize access against the owning profile or post.
- Fail closed if authorization cannot be checked.
- Do not expose permanent public object URLs.
- Prefer an authorized streaming endpoint for MVP-2.
- If short-lived download URLs are used, document their residual-access window after visibility changes or blocking.

Use bounded memory and streaming where appropriate.

No video, PDFs, message attachments, image transformations on demand, or CDN integration.

### B. Post visibility and blocking

Add post visibility:

- `MEMBERS`: visible to authenticated members.
- `CONNECTIONS`: visible to the author and currently accepted connections.

Migrate existing posts to `MEMBERS`, preserving MVP-1 behavior.

Visibility rules apply consistently to:

- Direct post retrieval.
- Member post listings.
- Home feed.
- Comments and likes.
- Media downloads.
- Notification target previews.

Require permission to view a post before liking or commenting on it.

After visibility changes:

- Evaluate future reads using the current policy.
- Avoid embedding post bodies or private media URLs in durable notification payloads.
- Handle now-inaccessible notification targets without disclosing content.

Add blocking:

- A member can block and unblock another member.
- Blocking either direction prevents profile discovery, profile access, content interaction, new connection requests,
  and new messages between the pair.
- Blocking removes any existing connection and cancels pending connection requests.
- Unblocking does not restore connections automatically.
- Prevent self-blocking.
- Keep repeated block/unblock operations idempotent.

Historical messages remain readable to their participants, but blocking prevents new messages. State this behavior
clearly.

Define a privacy decision table before implementation. Use it to drive authorization tests across services.

Do not trust asynchronous projections as the sole authority for privacy-sensitive access. Use an authoritative policy
check or a design with equivalent correctness. Document any unavoidable concurrency window across services without
claiming cross-service atomicity.

Avoid leaking private content through error messages, counts, previews, or logs.

### C. One-to-one messaging

Add a dedicated `messaging-service`.

Support:

- Starting a conversation with an accepted connection.
- One conversation per unordered member pair.
- Listing my conversations.
- Sending text messages.
- Cursor-based message history.
- Unread counts.
- Marking messages as read through a monotonic read position.
- Polling for new messages.

Limits:

- Two participants only.
- Text only.
- Maximum 4,000 characters per message.
- No message editing, deletion, attachments, typing indicators, presence, or group chats.
- No end-to-end encryption claim.

Authorization:

- Only participants can access a conversation or its messages.
- Recheck that the pair remains connected and unblocked when sending.
- After disconnection or blocking, preserve historical access but reject new sends.
- Derive the sender from the authenticated principal.

Correctness:

- Enforce conversation uniqueness with a database constraint.
- Require a client-generated message identifier.
- Enforce uniqueness by conversation, sender, and client message identifier.
- Repeating the same send request returns the original result.
- Reusing an identifier with different content returns a conflict.
- Use deterministic per-conversation ordering.
- Ensure the read position never moves backward.
- Validate that a requested read position belongs to that conversation.

Messaging events:

- Publish through the transactional outbox.
- Generate a generic “new message” notification without exposing message text.
- Keep unread message state authoritative in messaging-service.
- Prevent duplicate notifications during event replay.

Use REST polling for this release. Document a future path to WebSockets without implementing it.

### D. Reporting and moderation

Support authenticated reporting of:

- A post.
- A comment.

Report reasons:

- Spam.
- Harassment.
- Inappropriate content.
- Other, with a bounded explanation.

Keep reporting and moderation inside `content-service`; do not create a moderation microservice.

Support:

- One active report per reporter and target.
- A paginated moderation queue.
- Moderator dismissal of a report.
- Moderator hiding and restoring of content.
- A recorded reason for each moderation action.
- An audit history containing actor, action, target, reason, and timestamp.

Define separate moderation states so that restoring moderated content cannot resurrect content deleted by its author.

Use a trusted moderator role assigned by the identity provider. Ordinary users must not be able to self-assign it.

Rules:

- Only report content the reporter can currently access.
- Reporters cannot view other users’ reports.
- Authors cannot identify reporters through APIs.
- Moderators can inspect reported content through explicit audited endpoints.
- Moderation access must not imply access to private messages.
- Hidden posts and comments disappear from normal reads and interactions.
- Their media and notification previews must follow the same restriction.
- Notify an author when moderation changes the visibility of their content.

No automated AI moderation, account bans, appeals workflow, or private-message reporting in MVP-2.

## 4. Architecture boundaries

Preserve existing service ownership:

`member-service`

- Profiles.
- Connections.
- Blocking.
- Authoritative member relationship policies.
- References to avatar media IDs.

`content-service`

- Posts, comments, likes.
- Post visibility.
- References to post media IDs.
- Reports and moderation.

`media-service`

- Upload validation.
- Binary object lifecycle.
- Media metadata.
- Authorized delivery.
- Attachment reconciliation and cleanup.

`messaging-service`

- Conversations.
- Messages.
- Read positions.
- Messaging outbox events.

`notification-service`

- Existing notifications.
- New message and moderation notifications.
- Safe handling of inaccessible targets.

`api-gateway`

- Routing and existing edge responsibilities.
- Upload request limits.
- No business authorization logic that replaces service enforcement.

No cross-service database access or shared JPA entities.

Keep synchronous dependency graphs understandable. Avoid circular request chains; document media authorization and
attachment workflows with sequence diagrams.

## 5. Technology discipline

Required additions:

- An S3-compatible storage client.
- A local private object store.
- Image-validation tooling compatible with the project’s Java version.

Evaluate Spring Batch for restartable cleanup and reconciliation of abandoned media. Adopt it only if it provides a
clear benefit over a small durable worker. Record the decision.

Do not add Spring Integration, Scala, Play, Rest.li, Spark, Hadoop, HDFS, Pinot, Samza, Espresso, Voldemort, Couchbase,
Helix, Memcached, Ruby, C++, Akamai, or another framework merely to exercise the technology list.

Update `docs/technology-decisions.md` with:

- Dependencies added.
- Exact versions and compatibility evidence.
- Technologies still deferred.
- Concrete triggers for future adoption.

Retain the existing observability approach rather than adding competing tracing or logging stacks.

## 6. Reliability and data design

Apply existing outbox and consumer-deduplication patterns to all new events.

Requirements:

- Database constraints enforce concurrency invariants.
- At-least-once delivery is expected.
- Consumer side effects are idempotent.
- Events are versioned.
- Sensitive message bodies and private content stay out of generic events.
- Retries are bounded and observable.
- Dead-letter replay is documented and tested.

For media attachment across service boundaries:

- Define an explicit state machine.
- Make retries safe.
- Protect in-progress attachments from cleanup.
- Reconcile expired claims with the authoritative resource owner before deleting media.
- Never delete an object merely because a service is temporarily unavailable.

Document deletion and retention policies. Do not claim complete erasure while backups or retained records still exist.

## 7. API and migration compatibility

Preserve MVP-1 APIs wherever possible.

Add:

- Versioned API contracts for media, messaging, blocking, and moderation.
- Visibility fields with compatible defaults.
- Updated error contracts.
- Cursor pagination.
- Consistent validation and authorization responses.

Use backward-compatible database changes where feasible:

- Add schema elements before making code depend on them.
- Backfill existing data safely.
- Validate constraints after backfill when needed.
- Avoid destructive column removal in this release.
- Explain rollback limitations.

Test migration from a populated MVP-1 database, not only from an empty database.

Document changes that affect existing client behavior.

## 8. Security and resource limits

Continue validating authentication independently in every service.

Additionally:

- Authorize media independently of object-key secrecy.
- Prevent attachment of another member’s media.
- Prevent access to conversations through guessed identifiers.
- Check moderator permissions server-side.
- Protect internal service endpoints.
- Bound upload size, decoded-image size, message size, page size, and expensive queries.
- Add practical upload and message abuse controls.

If rate limits are instance-local, state that limitation. Do not describe them as cluster-wide limits.

Keep credentials, message content, private post content, and image metadata out of logs and traces.

Use explicit local-development defaults and separate production configuration requirements.

## 9. Development phases and gates

### Phase 0 — Audit and release design

Deliver:

- MVP-1 baseline results.
- MVP-2 acceptance checklist.
- Privacy decision table.
- Updated architecture and sequence diagrams.
- Media and messaging state models.
- Migration and compatibility plan.

Gate:

- Identify prerequisite failures.
- Fix blockers needed by subsequent phases.
- Preserve a working baseline before changing behavior.

### Phase 1 — Blocking and post visibility

Implement:

- Blocking APIs and database constraints.
- Connection cleanup on block.
- Post visibility.
- Authorization across content reads and interactions.
- Safe notification target handling.

Gate:

- Existing posts retain their original visibility.
- Connection-only posts are inaccessible to unrelated members.
- Blocking prevents the defined interactions.
- Unblocking does not restore old connections.
- Privacy tests pass across direct reads and listings.

### Phase 2 — Media

Implement:

- Media-service and private object storage.
- Validated image uploads.
- Avatar and post attachment flows.
- Authorized downloads.
- Cleanup and reconciliation.

Gate:

- Valid images can be attached and viewed by authorized members.
- Oversized, malformed, and unsupported files are rejected.
- Users cannot attach or download unauthorized media.
- Restricted or hidden content does not expose its images.
- Interrupted attachment workflows recover.
- Cleanup cannot delete valid attachments.

### Phase 3 — Messaging

Implement:

- Conversation creation and uniqueness.
- Idempotent message sending.
- History pagination.
- Read positions and unread counts.
- Relationship checks.
- Message notifications.

Gate:

- Connected members can exchange messages.
- Concurrent conversation creation produces one conversation.
- Retried sends produce one message.
- Nonparticipants cannot read messages.
- Blocking or disconnection prevents new sends.
- Historical access follows the documented policy.
- Read positions cannot move backward.

### Phase 4 — Reporting and moderation

Implement:

- Reports.
- Moderator queue and actions.
- Audit history.
- Content visibility enforcement.
- Author notifications.

Gate:

- Ordinary users cannot access moderator endpoints.
- Reporters cannot discover other reports.
- Hidden content disappears from all ordinary access paths.
- Restoring moderation state cannot restore author-deleted content.
- Actions are audited and notifications are deduplicated.

### Phase 5 — Failure testing and operational hardening

Implement or extend:

- Security regression suite.
- Oracle/Kafka/object-store integration tests.
- Cross-service contract tests.
- Load tests.
- Dashboards and alerts.
- Outage and recovery tests.

Gate:

- Broker and consumer failures preserve committed business operations.
- Authorization dependency failures fail closed.
- Object-store outages do not produce false upload success.
- Database and service restarts preserve message/read state.
- No unbounded downstream call per feed item or message.
- Performance results include dataset, hardware, concurrency, and duration.

### Phase 6 — Deployment and release

Update:

- Docker Compose.
- Container builds.
- Local Kubernetes configuration.
- Storage persistence.
- Secret references.
- CI verification.
- Backup/restore and rollback procedures.
- IntelliJ HTTP examples and end-to-end scripts.

Gate:

- Fresh setup works.
- Upgrade from populated MVP-1 data works.
- End-to-end tests pass against the deployed system.
- Application restarts preserve persisted data.
- Deployment blockers are explicitly reported.
- Local single-node infrastructure is not described as production high availability.

## 10. Testing strategy

Use existing test conventions.

Require:

- Unit tests for policy rules and state transitions.
- Security tests for ownership, visibility, blocking, conversation membership, and moderator permissions.
- Real Oracle integration tests for migrations and constraints.
- Kafka integration tests for outbox recovery and duplicate delivery.
- Object-store integration tests for upload and download behavior.
- End-to-end tests through the gateway.

Do not use mocks as the only evidence for cross-service correctness.

Use deterministic asynchronous waits and isolated test data.

Critical end-to-end scenario:

1. Create three members and a moderator identity.
2. Connect members A and B.
3. Upload avatars and post images.
4. Publish a connection-only post as A.
5. Verify B can access it and C cannot, including its images.
6. Exchange messages between A and B.
7. Retry a send and verify only one message exists.
8. Advance B’s read position and verify unread counts.
9. Block B as A.
10. Verify new messages and restricted content access fail.
11. Verify historical messages remain accessible to participants.
12. Unblock B and verify the connection is not automatically restored.
13. Reconnect A and B.
14. Report a post as B.
15. Hide the post as the moderator.
16. Verify normal post, comment, media, and preview access is restricted.
17. Restore the post and verify its original visibility policy applies.
18. Replay notification events and verify no duplicates.
19. Restart services and verify persisted state.
20. Upgrade a populated MVP-1 database and rerun applicable regression tests.

## 11. Documentation and progress

Maintain:

- `docs/mvp2-plan.md`
- `docs/mvp2-progress.md`
- `docs/privacy-policy-matrix.md`
- `docs/mvp2-verification.md`

Update existing architecture, ADRs, technology decisions, runbook, and API documentation rather than duplicating them.

At each phase boundary:

- Summarize implemented behavior.
- Record checks and their actual results.
- Separate PASS, FAIL, BLOCKED, and NOT RUN.
- Record unresolved limitations.
- State the next executable step.

If interrupted, save a checkpoint with the current phase, completed acceptance criteria, failures, and next action.
Resume from repository evidence rather than regenerating completed work.

## 12. Definition of done

MVP-2 is complete when:

- All four feature areas work through real APIs.
- MVP-1 regression tests still pass.
- Privacy rules are enforced across content, media, messaging, and notifications.
- Database constraints and integration tests establish concurrency correctness.
- Media attachment and cleanup recover safely from interruptions.
- Message sending is idempotent.
- Moderation actions are authorized and audited.
- A populated MVP-1 installation can be upgraded.
- Deployment and recovery instructions reflect executed checks.
- Required features contain no placeholders.
- Remaining environment blockers and production limitations are stated explicitly.

Start by inspecting the existing MVP-1 repository and running its baseline verification. Then implement MVP-2 phase by
phase until the acceptance criteria are satisfied.
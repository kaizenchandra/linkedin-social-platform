Paste this into the same IntelliJ Codex project. MVP-6 focuses on **account lifecycle, personal-data export, account
deletion, and security hardening**, with durable workflows across the existing services.

You are my principal backend engineer and implementation agent working inside IntelliJ.

Extend the existing professional networking platform from MVP-5 to MVP-6 through incremental, verified development.

Implement working code, migrations, tests, API contracts, deployment updates, and operational documentation. Do not stop
after planning or scaffolding.

## 1. Starting point and execution rules

The intended existing platform contains:

- Java 21, Spring Boot 4.x, Spring Cloud, Spring Security, and Maven.
- Gateway, member, content, media, messaging, notification, and hiring services.
- Keycloak for OIDC.
- Oracle with service-owned schemas.
- Kafka with transactional outbox publishing and idempotent consumers.
- Private S3-compatible object storage.
- Docker Compose and local Kubernetes.
- Prometheus, Grafana, OpenTelemetry, and Zipkin.

Previous releases should provide:

- Profiles, connections, follows, posts, media, and messaging.
- Privacy controls, blocking, moderation, and saved items.
- Companies, jobs, applications, discovery, and job alerts.
- Live message and notification streams with durable replay.

Treat this as intended context, not evidence of implementation.

Before editing:

- Read applicable AGENTS.md instructions.
- Inspect actual code, contracts, migrations, ADRs, and progress records.
- Run available baseline checks.
- Identify incomplete prerequisites.
- Repair prerequisites required by MVP-6.
- Preserve unrelated user work.

Continue automatically after phase verification gates pass. Ask only when missing information genuinely blocks progress.

Do not rewrite working services, perform unrelated upgrades, weaken authorization, or claim unexecuted checks passed.

Do not operate on real user accounts or shared environments without explicit authorization. Use disposable local
identities and fixtures for lifecycle testing.

## 2. MVP-6 objective

Deliver account lifecycle and personal-data controls:

1. Account deactivation and reactivation.
2. Authenticated personal-data exports.
3. Account deletion with a cancellation period.
4. Durable cross-service cleanup and reconciliation.
5. Session and stream revocation integration.
6. Operational evidence for recovery, retention, and failure handling.

Exclude:

- A production frontend.
- Billing and subscriptions.
- Enterprise SSO onboarding.
- Custom password storage or cryptography.
- Automated legal compliance decisions.
- A new generic workflow engine.
- A new microservice solely for account lifecycle.
- Platform-wide administrator impersonation.

Implement technical controls and document their limits. Do not claim legal compliance or complete anonymization without
supporting evidence.

## 3. Architecture and ownership

Use `member-service` as the owner of:

- Platform account lifecycle state.
- Lifecycle operation records.
- Deletion scheduling.
- Export orchestration.
- Per-service progress and reconciliation.

Keycloak remains authoritative for identity credentials and authentication sessions.

Each existing service owns cleanup and export of its own data.

Rules:

- No cross-service database access.
- No distributed database transactions.
- Use explicit, versioned commands and acknowledgements.
- Use transactional outbox publishing and idempotent handlers.
- Keep durable workflow state separate from traces.
- Do not use Kafka delivery as proof that cleanup completed.

Persist:

- Operation ID.
- Member ID.
- Lifecycle generation/version.
- Requested action.
- Current status.
- Per-service status.
- Retry information.
- Timestamps.
- Sanitized failure details.

Document the workflow with state and sequence diagrams.

## 4. Account state machine

Implement:

- `ACTIVE`
- `DEACTIVATED`
- `DELETION_REQUESTED`
- `DELETING`
- `DELETED`

Define allowed transitions:

- ACTIVE → DEACTIVATED.
- DEACTIVATED → ACTIVE.
- ACTIVE or DEACTIVATED → DELETION_REQUESTED.
- DELETION_REQUESTED → previous state when cancelled within the cancellation period.
- DELETION_REQUESTED → DELETING after the deadline.
- DELETING → DELETED only after required completion checks.

DELETING and DELETED cannot be cancelled or reactivated through ordinary APIs.

Use optimistic concurrency or explicit locking to resolve competing requests.

Make repeated lifecycle commands idempotent.

A lifecycle generation must prevent an old deactivate, reactivate, or cleanup event from overwriting a newer decision.

Expose operation status to the account owner through a narrowly authorized endpoint.

## 5. Account deactivation

Deactivation is reversible.

On deactivation:

- Reject new social, messaging, hiring, upload, and job-alert mutations by the member.
- Hide the profile from discovery.
- Hide authored social posts and associated media from ordinary readers.
- Stop new notifications and job alerts for the member.
- Revoke identity-provider sessions where supported.
- Terminate or bound the remaining lifetime of live streams.
- Preserve data for reactivation.

Historical messages:

- Remain accessible to other conversation participants under existing rules.
- Show the sender as unavailable where appropriate.
- Do not expose the deactivated profile through message hydration.

Hiring:

- Existing applications remain visible to authorized company reviewers under the established application policy.
- Existing company jobs remain company-owned.
- Deactivated recruiters lose access.
- Do not silently delete company data.

Company ownership:

- Require transfer of ownership before deactivation or deletion when the member is the sole owner.
- Return a clear conflict listing only companies the member is authorized to manage.
- Do not automatically transfer ownership to an arbitrary recruiter.

Reactivation:

- Restore access through the identity provider and platform lifecycle flow.
- Restore content only if it was hidden solely because of account state.
- Preserve independent author deletion, moderation, blocking, and job lifecycle decisions.
- Do not replay old notifications or create historical job alerts.

## 6. Restricted lifecycle access

Deactivated accounts must have a secure way to:

- View account status.
- Reactivate.
- Request an export.
- Request deletion.
- Cancel a pending deletion.

Keep authentication and platform authorization separate.

Do not disable identity-provider login in a way that makes the recovery flow impossible unless a tested alternative
exists.

Restrict DEACTIVATED and DELETION_REQUESTED users to explicitly allowed lifecycle endpoints.

Require recent authentication for:

- Export download authorization.
- Deletion request.
- Deletion cancellation.
- Reactivation.

Verify the identity provider’s supported reauthentication mechanism. Do not treat token refresh alone as proof of recent
authentication.

Use server-validated claims and configuration. Do not trust a client-provided “reauthenticated” flag.

## 7. Lifecycle enforcement across services

Every service must enforce lifecycle restrictions, including internal entry points and asynchronous consumers.

An authenticated JWT alone is insufficient evidence that an account is currently active.

Define:

- How services obtain current lifecycle state.
- Whether any caching is permitted.
- Cache invalidation and maximum staleness.
- Failure behavior.
- Treatment of requests already in progress.

Do not claim instantaneous cross-service revocation unless demonstrated.

Before irreversible deletion:

- Establish a durable write fence.
- Prevent new operations from recreating account-owned data.
- Drain or invalidate in-flight work according to a documented protocol.
- Reconcile writes that raced with the fence.
- Reject stale lifecycle generations.
- Ensure delayed Kafka events cannot recreate deleted records.

Do not claim deletion complete based solely on a one-time cleanup scan.

Privacy-sensitive reads must fail closed when required lifecycle authorization cannot be established.

## 8. Personal-data export

Provide asynchronous APIs to:

- Request an export.
- Retrieve export status.
- Download a completed export.
- Delete an available export.

Allow one active export per member.

Use a configurable cooldown to prevent repeated expensive exports. Return a clear retry time rather than silently
ignoring requests.

Export format:

- ZIP containing documented JSON files.
- A manifest describing schema versions, included sections, extraction times, and omissions.
- Owned media files where practical and authorized.
- Checksums for archive entries.

Include:

- Profile and experience information.
- The member’s relationship and preference records.
- Authored posts and comments.
- Likes and saved-item references.
- Conversations and messages the member is entitled to read.
- Applications and their submitted snapshots.
- Saved searches, follows, and notification preferences.

Do not include:

- Password hashes, tokens, secrets, or internal credentials.
- Other users’ private profiles.
- Private reporter identities.
- Recruiter-only application information unrelated to the member’s own applications.
- Entire company datasets merely because the member is an owner.
- Internal security or moderation notes not available to the member.

Create an explicit field-level export policy before implementation.

Consistency:

- Record per-service extraction boundaries.
- Use a consistent snapshot within each service where supported.
- Do not claim one atomic snapshot across all databases.
- Explain possible cross-service timing differences.

Do not silently mark an export complete if a required section failed.

## 9. Secure export storage

Keep export archives private.

Requirements:

- Generate unpredictable object keys.
- Authorize every status and download request.
- Prevent account-to-account access by guessing operation IDs.
- Do not log archive contents or download credentials.
- Use bounded-memory streaming.
- Sanitize ZIP entry names.
- Clean up partial archives and failed uploads.

Default completed-export expiry: 24 hours.

Prefer an authenticated download endpoint. If using signed URLs:

- Keep their lifetime short.
- Document their residual access window.
- Do not claim immediate revocation after URL issuance.

Deletion requests must revoke available exports and cancel or safely terminate in-progress exports.

Never route export archives through a public media endpoint.

## 10. Account deletion

Use a configurable cancellation period, defaulting to seven days for local product behavior. This is a product default,
not a legal requirement.

On request:

- Require recent authentication.
- Require explicit confirmation through the API.
- Validate company-ownership prerequisites.
- Persist the previous account state.
- Move the account to DELETION_REQUESTED.
- Restrict access and revoke sessions as defined.
- Return the scheduled deletion time.

Cancellation:

- Allow only before irreversible deletion starts.
- Restore the previous lifecycle state.
- Do not recreate revoked sessions automatically.
- Resolve cancellation versus worker-start races transactionally.

After the deadline:

- Move to DELETING.
- Apply the durable write fence.
- Run service-specific cleanup.
- Verify completion.
- Finalize identity-provider deletion after necessary platform work.

Do not mark DELETED if required cleanup remains failed or unverified.

## 11. Data disposition policy

Create and implement a service-by-service matrix.

Default behavior:

Member data:

- Remove profile details, experience, connections, follows, blocks, and preferences.
- Retain only minimal lifecycle tombstones required to reject stale work.

Content:

- Remove authored post and comment bodies and owned attachments.
- Preserve minimal structural tombstones where threads require them.
- Remove the member’s likes and saved-item records.
- Do not delete other members’ independent content unnecessarily.

Media:

- Delete unreferenced owned objects.
- Handle incomplete multipart uploads and temporary objects.
- Do not delete company-owned logos solely because their uploader is deleted.
- Verify ownership semantics before cleanup.

Messaging:

- Remove the deleted member’s private read state and preferences.
- Preserve conversation history available to remaining participants.
- Replace profile presentation with a deleted-member label.
- Document that retained message text may still contain identifying information.
- Do not describe this as complete anonymization.

Notifications:

- Remove the member’s notification inbox and replay history.
- Prevent new notification creation.

Hiring:

- Remove personal saved jobs, searches, follows, and recruiter memberships.
- Preserve company-owned jobs.
- Remove or redact the deleted applicant’s profile snapshot and cover note.
- Retain minimal application history under the documented retention policy.
- Do not expose deleted profile information through historical recruiter responses.

Audit records:

- Retain only justified fields for a configured period.
- Remove unnecessary personal payloads.
- Document what remains and why.

Do not use indefinite soft deletion as a substitute for the requested cleanup.

## 12. Backups and replay safety

Document the difference between:

- Live-system deletion.
- Object-storage deletion.
- Retained audit records.
- Backup expiration.

Do not promise immediate erasure from existing backups.

Provide a recovery procedure that reapplies deletion decisions after restoring an older backup.

Maintain a minimal deletion ledger or equivalent mechanism that:

- Survives the relevant restore workflow.
- Prevents restored accounts from becoming active before reconciliation.
- Contains no unnecessary profile data.

Test that replaying old business events cannot recreate:

- Deleted profiles.
- Notifications.
- Saved searches.
- Media attachments.
- Other account-owned records.

Treat lifecycle tombstones and deletion-ledger retention as explicit operational decisions.

## 13. Reliability and technology discipline

Keep the established Java and Spring stack.

Evaluate Spring Batch for:

- Restartable export extraction.
- Large cleanup jobs.
- Retention processing.

Use it only where checkpointing and chunk processing improve the actual implementation.

Do not add a workflow platform, analytics cluster, or new language runtime merely for this release.

Required workflow behavior:

- Idempotent commands and acknowledgements.
- Bounded retries.
- Durable checkpoints.
- Safe worker concurrency.
- Sanitized failure reporting.
- Controlled operator retry.
- Reconciliation of missing acknowledgements.
- No false success after partial failure.

Authenticate operator endpoints and audit their use. Operators must not be able to bypass lifecycle invariants through a
generic “force complete” action.

## 14. Development phases

### Phase 0 — Baseline and policy

Deliver:

- MVP-5 baseline results.
- Lifecycle state machine.
- Access-policy matrix.
- Field-level export policy.
- Data disposition and retention matrix.
- Write-fencing and recovery design.

Gate:

- Resolve required prerequisites.
- Identify irreversible operations and their local test fixtures.

### Phase 1 — Deactivation and lifecycle enforcement

Implement lifecycle APIs, service enforcement, session integration, and stream termination behavior.

Gate:

- Restricted accounts cannot perform ordinary mutations.
- Existing tokens do not bypass lifecycle checks.
- Reactivation restores only eligible access.
- Company ownership prerequisites are enforced.
- Independent moderation and deletion states remain intact.

### Phase 2 — Export

Implement durable export orchestration, per-service extraction, private archives, and expiry.

Gate:

- Exports contain the documented fields.
- Another member cannot access them.
- Failed sections prevent false completion.
- Large exports remain memory-bounded.
- Retrying work does not duplicate or corrupt archive entries.

### Phase 3 — Deletion

Implement scheduling, cancellation, write fencing, service cleanup, and identity finalization.

Gate:

- Cancellation races are deterministic.
- Interrupted cleanup resumes safely.
- Delayed writes and events cannot recreate deleted data.
- Required failures prevent final completion.
- Export access is revoked.

### Phase 4 — Reconciliation and recovery

Implement retention workers, operator status tools, deletion-ledger handling, and restore reconciliation.

Gate:

- Duplicate commands and acknowledgements are safe.
- Lost acknowledgements can be reconciled.
- A restored backup cannot silently reactivate deleted accounts.
- Retained records match the documented disposition policy.

### Phase 5 — Hardening and release

Update integration tests, deployment configuration, CI, dashboards, runbooks, and IntelliJ HTTP examples.

Gate:

- Fresh setup works.
- Upgrade from populated MVP-5 data works.
- Existing regression tests pass.
- End-to-end lifecycle workflows pass against the deployed local environment.
- Unexecuted checks are explicitly marked blocked or not run.

## 15. End-to-end acceptance scenario

Automate with disposable accounts:

1. Create a member with posts, images, messages, saved items, and an application.
2. Open live streams on two devices.
3. Deactivate the account.
4. Verify ordinary mutations fail and streams follow the revocation policy.
5. Verify historical recipient message access remains correct.
6. Reactivate and verify independent moderation restrictions remain.
7. Request an export and validate its manifest and access controls.
8. Interrupt export processing and verify safe recovery.
9. Request deletion and verify restricted access.
10. Cancel before the deadline and verify restoration of the previous state.
11. Request deletion again and advance the test clock.
12. Race a write against deletion fencing.
13. Interrupt a cleanup worker and verify resumption.
14. Replay old events and verify no deleted data is recreated.
15. Verify retained message and application records follow the disposition matrix.
16. Verify identity-provider finalization.
17. Restore disposable backup data and apply deletion reconciliation before serving traffic.
18. Verify a sole company owner must transfer ownership before deactivation or deletion.
19. Run MVP-1 through MVP-5 regression tests.

Use real Oracle, Kafka, object-store, and identity-provider integration tests where applicable.

Use an injectable clock for deadlines. Do not wait seven real days or weaken production rules for tests.

## 16. Progress and definition of done

Maintain:

- `docs/mvp6-plan.md`
- `docs/mvp6-progress.md`
- `docs/mvp6-verification.md`
- `docs/account-lifecycle-policy.md`
- `docs/data-disposition-matrix.md`

Update existing ADRs, contracts, architecture, and runbooks.

At each phase boundary:

- Summarize implemented behavior.
- Record actual checks.
- Separate PASS, FAIL, BLOCKED, and NOT RUN.
- Record remaining risks and the next executable action.

If interrupted, save a precise checkpoint and resume from repository evidence.

MVP-6 is complete when:

- Deactivation and reactivation enforce the defined policy.
- Exports are authorized, complete, private, and recoverable.
- Deletion is cancellable before execution and restartable afterward.
- Stale writes and event replay cannot recreate deleted account data.
- Retention and backup limitations are documented and tested where feasible.
- Existing features remain compatible.
- Upgrade and local deployment checks are supported by evidence.

Do not claim legal compliance, immediate global revocation, or complete erasure beyond the verified implementation.

Start by inspecting MVP-5 and running baseline verification. Then implement MVP-6 phase by phase until the acceptance
criteria are satisfied.
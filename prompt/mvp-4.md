Paste this into the same IntelliJ Codex project. MVP-4 focuses on **member and company following, saved posts and jobs, personalized discovery, and in-app job alerts**.

You are my principal backend engineer and implementation agent working inside IntelliJ.

Extend the existing professional networking platform from MVP-3 to MVP-4 through incremental, verified development.

Implement working code, migrations, tests, API contracts, deployment updates, and operational documentation. Do not stop after planning or scaffolding.

## 1. Starting point and execution rules

The intended existing platform contains:
- Java 21, Spring Boot 4.x, Spring Cloud, Spring Security, and Maven.
- Gateway, member, content, media, messaging, notification, and hiring services.
- Keycloak for OIDC.
- Oracle with service-owned schemas.
- Kafka with transactional outbox publishing and idempotent consumers.
- Private S3-compatible object storage.
- Docker Compose and local Kubernetes deployment.
- Prometheus, Grafana, OpenTelemetry, and Zipkin.

Previous releases should provide:
- Profiles, connections, posts, comments, likes, and chronological feeds.
- Visibility controls, blocking, images, and private messaging.
- Content reporting and moderation.
- Companies, recruiter membership, jobs, applications, and hiring moderation.

Treat these as intended capabilities, not proof of implementation.

Before editing:
- Read applicable AGENTS.md instructions.
- Inspect the repository, migrations, contracts, ADRs, and progress records.
- Run available baseline checks.
- Identify missing prerequisites and existing failures.
- Repair prerequisites required by MVP-4.
- Preserve unrelated files and user changes.

Continue between phases after verification gates pass. Ask questions only for genuine blockers.

Do not rewrite working services, perform unrelated upgrades, disable security, or claim unexecuted checks passed.

Respect permission boundaries. Default deployment targets are local Docker Compose and a dedicated local Kubernetes environment.

## 2. MVP-4 objective

Improve discovery and repeat engagement through:

1. Following members and companies.
2. An expanded chronological home feed.
3. Private saved posts and saved jobs.
4. Explainable member and company suggestions.
5. Saved job searches and in-app job alerts.

Keep this release bounded.

Exclude:
- Machine-learning ranking, embeddings, RAG, and external AI APIs.
- Advertising, payments, premium accounts, and sponsored content.
- Email, SMS, push delivery, and WebSockets.
- Company-authored social posts.
- Public follower lists or public bookmark collections.
- New analytics platforms or search clusters without measured justification.
- Frontend development.
- Changes to hiring decisions or automated candidate evaluation.

“Personalized” means explicit preferences and transparent rules, not inferred sensitive traits or machine learning.

## 3. Architecture and ownership

Extend existing services.

`member-service`
- Member follows.
- Member discovery suggestions.
- Authoritative connection and blocking policies.

`content-service`
- Saved posts.
- Expanded chronological feed.
- Content authorization.

`hiring-service`
- Company follows.
- Saved jobs.
- Company suggestions.
- Saved job searches.
- Durable job-alert matching and match records.

`notification-service`
- Job-alert notifications.
- Preferences controlling job-alert notification creation.

Do not create follow, bookmark, recommendation, or search microservices for this release.

Rules:
- No cross-service database joins.
- No shared persistence entities.
- Authenticate internal APIs.
- Keep service-owned authorization authoritative.
- Use REST for required queries and Kafka for asynchronous notifications.
- Preserve existing outbox, deduplication, tracing, and migration patterns.
- Keep privacy-sensitive eligibility out of eventually consistent projections unless every result is reauthorized before exposure.

Document significant decisions with concise ADRs.

## 4. Member following

Add a one-way follow relationship independent of connections.

Support:
- Follow a member.
- Unfollow a member.
- List members I follow.
- Retrieve my follow status for a member.

Rules:
- Prevent self-following.
- Prevent duplicate follows through a database constraint.
- Make repeated follow and unfollow requests idempotent.
- Reject following when either member has blocked the other.
- Blocking removes follows in both directions.
- Unblocking does not restore follows.
- Connection removal does not remove an independently created follow.
- Following does not create a connection or grant messaging permission.

Do not expose who follows another member in MVP-4.

Keep current connection APIs and behavior compatible.

## 5. Company following

Support:
- Follow and unfollow a company.
- List companies I follow.
- Retrieve my follow status for a company.

Rules:
- One follow per member/company pair.
- Repeated commands are idempotent.
- Following does not grant recruiter or owner privileges.
- Company administrators cannot retrieve follower identities.
- Do not expose public follower counts in this release.

Company follows influence discovery and provide an explicit filter for job search.

Following a company does not automatically create a job alert. Members must explicitly create a saved search with alerts enabled.

Do not introduce company-authored posts.

## 6. Expanded chronological feed

Extend the existing home feed to include:
- My posts.
- Posts from accepted connections.
- MEMBERS-visible posts from members I follow.

Visibility:
- Following never grants access to CONNECTIONS-only posts.
- Blocking and moderation override feed inclusion.
- Saved status does not grant access to a post.
- A post must appear only once when its author is both connected and followed.
- Existing media, comment, and interaction authorization still applies.

Keep ordering chronological using a stable `(createdAt, id)` cursor.

Preserve the existing feed API where possible. Add optional fields compatibly.

Implementation:
- Obtain eligible author relationships through bounded internal APIs.
- Avoid one relationship check or profile lookup per post.
- Use batched authorization and hydration.
- Handle Oracle query limits explicitly.
- Do not silently truncate large follow or connection sets.
- If introducing a documented product limit, enforce it in the write APIs.

Pagination:
- Filter inaccessible content before returning it.
- Advance the cursor over scanned candidates, not only returned items.
- Use a bounded scan budget.
- Explain that changing relationships can change later pages; do not claim snapshot consistency without implementing it.
- Never emit a cursor that repeatedly scans the same rejected candidates.

If authoritative relationship checks fail, return the documented retriable error instead of exposing unverified content.

## 7. Saved posts and jobs

Saved items are private to their owner.

### Saved posts

Support:
- Save a currently accessible post.
- Remove a saved post.
- List my saved posts ordered by save time.

Enforce one saved record per member/post pair.

Recheck current post authorization whenever listing or opening saved content.

If a post becomes deleted, hidden, blocked, or inaccessible:
- Do not return its body, author details, media, or previous preview.
- Omit it from the normal saved-post response.
- Preserve enough internal state for safe unsaving and later cleanup.
- Do not reveal inaccessible-item counts.

### Saved jobs

Support:
- Save a currently visible published job.
- Remove a saved job.
- List my saved jobs.

Enforce one saved record per member/job pair.

If a job closes:
- Show a safe summary with a closed status.
- Prevent application submission.

If a job is moderated or otherwise inaccessible:
- Omit its content from normal saved-job responses.
- Do not serve a stale cached preview.

General requirements:
- Make save/unsave operations idempotent.
- Use deterministic pagination by save timestamp and identifier.
- Return only the authenticated member’s saved items.
- Do not expose bookmark ownership to authors, recruiters, or company owners.
- Do not let bookmarks bypass existing authorization.

## 8. Explainable discovery

Provide small, bounded suggestion APIs.

### Member suggestions

Suggest eligible members using:
1. Mutual accepted connections.
2. Stable tie-breaking.

Exclude:
- The acting member.
- Existing connections.
- Already-followed members.
- Members with a pending connection request involving the actor.
- Either direction of blocking.
- Profiles unavailable under current authorization.

Return a concise reason such as “Connections in common.”

Keep mutual-connection identities and exact counts out of the response unless an existing privacy policy explicitly permits them.

Bound candidate exploration. Document any candidate limit and its effect on completeness.

Do not use protected characteristics, message content, hiring applications, or private activity.

### Company suggestions

Suggest companies using:
- Explicit company industry and location.
- Optional industry/location filters supplied by the member.
- Stable ordering and tie-breaking.

Exclude already-followed companies.

Do not infer an industry from private application history or message content.

If too few candidates exist, return fewer results. Do not fabricate suggestions or silently broaden privacy rules.

Return explanations based on actual matching criteria.

### Verification

Test ranking rules with deterministic fixtures.

Measure query cost before introducing caches. If caching is necessary:
- Document TTL and invalidation.
- Keep authorization checks current.
- Include all relevant user/filter context in cache keys.
- Never use a cached result to bypass blocking.

## 9. Saved job searches

Add private saved searches with:
- A member-defined name.
- Optional keywords.
- Optional explicit company IDs.
- Optional location.
- Optional work arrangement.
- Optional employment type.
- An alerts-enabled flag.

Use the same matching semantics as interactive job search.

For predictable alerts:
- Resolve company selections to explicit IDs.
- Do not use a dynamically changing “companies I follow” filter inside a saved alert.
- The UI/API client may build an explicit selection from followed companies.

Support:
- Create.
- List.
- Update.
- Delete.
- Enable and disable alerts.

Default product limits:
- At most 10 saved searches per member.
- At most 20 explicitly selected companies per search.
- Reuse existing bounded keyword and field validation.

Persist a search version whenever its matching criteria change.

Editing, enabling, or creating a saved search applies prospectively. Do not send historical backfill alerts by default.

Deleting a saved search prevents new alerts but does not require deleting already delivered generic notifications.

## 10. Reliable job alerts

Create in-app alerts when a newly published job matches an active saved search.

Only a transition from draft to published triggers matching.

Do not send new alerts merely because:
- A published job was edited.
- A hidden job was restored.
- A worker replayed an event.
- A saved search was enabled.

Persist an immutable publication snapshot with the job publication event for reproducible matching. Before delivery, verify that the job is still published, unexpired, and not moderated.

Use a durable matching workflow:
- Consume publication events idempotently.
- Create or resume a matching work item.
- Process saved searches in bounded batches.
- Persist progress so a restart does not restart the entire scan.
- Persist match records and notification outbox events atomically.
- Apply backpressure and bounded retries.
- Make failed work observable and replayable.

Eligibility:
- The saved search must have been active, with its current criteria version, when the job was published.
- It must remain enabled when a new match is committed.
- A criteria update invalidates unfinished work for the previous version.
- Use persisted versions and timestamps, with a documented boundary rule.
- Do not match an old publication against newly created criteria.

If multiple saved searches match the same job:
- Produce at most one job-alert notification per member and job.
- Enforce this with a database uniqueness rule.
- Choose a deterministic matching reason.
- Avoid storing a growing list of all matched searches unless required.

Disabling alerts:
- Prevent new match creation after the disable operation commits.
- Suppress queued delivery where the current preference or search state makes it ineligible.
- Document that already delivered notifications are not recalled and that delivery already in progress has a defined race boundary.

Do not promise instantaneous cancellation across service boundaries.

## 11. Notification preferences

Add a narrow preference for job-alert notifications:
- Enabled by default only for members who explicitly enable a saved-search alert.
- Members can disable all job-alert notifications.

Do not modify security or transactional notifications as a side effect.

Job-alert notifications:
- Contain minimal identifiers and safe generic text.
- Resolve current job authorization when opened.
- Do not preserve a moderated job’s description or stale preview.
- Remain deduplicated during replay.

Keep match state authoritative in hiring-service and notification delivery state authoritative in notification-service.

Define behavior when eligibility or preference checks are unavailable:
- Retry or suppress conservatively.
- Never silently assume authorization.

## 12. Reliability and technology discipline

Use existing Java, Spring, Oracle, and Kafka conventions.

Evaluate Spring Batch for durable matching only if its checkpointing and restart model fit the event-triggered workload. A small durable worker is acceptable when simpler.

Do not run an unbounded full scan inside a Kafka listener or HTTP request.

Do not introduce Spark, Samza, Hadoop, HDFS, Pinot, a vector database, or an ML platform for rule-based discovery.

Before adding a dependency:
- Verify compatibility.
- Pin its version.
- Explain the concrete need.
- Update technology decisions.

Preserve:
- At-least-once delivery assumptions.
- Transactional outbox publishing.
- Atomic consumer deduplication.
- Bounded retries and dead-letter handling.
- Authenticated internal APIs.
- Backward-compatible event evolution.

## 13. Implementation phases

### Phase 0 — Baseline and design

Deliver:
- MVP-3 baseline verification.
- MVP-4 acceptance checklist.
- Follow and privacy decision tables.
- Feed query plan.
- Saved-search versioning rules.
- Alert workflow and cancellation semantics.
- Migration plan.

Gate:
- Resolve prerequisites required by Phase 1.
- Record unrelated pre-existing failures separately.

### Phase 1 — Following

Implement member and company follows, blocking integration, constraints, and APIs.

Gate:
- Concurrent duplicate follows create one relationship.
- Blocking removes both directions of member follows.
- Unblocking restores nothing automatically.
- Company following grants no company permissions.

### Phase 2 — Feed and saved items

Implement expanded feed eligibility, saved posts, and saved jobs.

Gate:
- Followed-member posts respect visibility.
- Connected-and-followed authors do not duplicate feed items.
- Saved content cannot bypass authorization.
- Closed saved jobs show the documented safe state.
- Pagination advances correctly through inaccessible items.

### Phase 3 — Discovery

Implement bounded member and company suggestions with explanations.

Gate:
- Exclusion rules hold.
- Suggestions are deterministic for fixed inputs.
- No private hiring or messaging data influences results.
- Query measurements support the selected implementation.

### Phase 4 — Saved searches and alerts

Implement saved-search CRUD, versioning, durable matching, preferences, and notifications.

Gate:
- A matching publication produces one alert.
- Multiple matching searches still produce one alert.
- Edits and replays do not generate duplicate alerts.
- Disabling, changing, or deleting a search follows documented rules.
- Worker restarts resume from durable progress.
- Closed or moderated jobs do not generate newly authorized alerts.

### Phase 5 — Hardening

Extend:
- Security regression tests.
- Oracle and Kafka integration tests.
- Failure and replay tests.
- Contract tests.
- Load tests.
- Metrics and dashboards.

Measure:
- Feed latency and scanned-versus-returned candidates.
- Discovery query cost.
- Matching backlog and oldest pending-work age.
- Publication-to-alert latency.
- Retry and deduplication outcomes.

Do not use member IDs, job IDs, or search IDs as metric labels.

Gate:
- No lost committed matches after failures.
- No unbounded processing or downstream fan-out.
- Existing release regression tests pass.
- Report measured results with dataset, hardware, concurrency, and duration.

### Phase 6 — Deployment and release

Update Compose, Kubernetes, migrations, CI, runbooks, and IntelliJ HTTP examples.

Gate:
- Fresh setup works.
- Upgrade from populated MVP-3 data works.
- End-to-end tests pass against the deployed environment.
- Application and worker restarts preserve progress.
- Deployment blockers are explicitly reported.

## 14. End-to-end acceptance scenario

Automate this workflow through the gateway:

1. Create members A, B, and C and two companies.
2. Connect A and B.
3. Follow C as A without connecting.
4. Publish MEMBERS and CONNECTIONS posts as C.
5. Verify A sees only C’s MEMBERS posts.
6. Follow B as A and verify B’s posts appear once.
7. Save an accessible post.
8. Restrict or hide it and verify saved-item access exposes no content.
9. Block C and verify follow removal and feed exclusion.
10. Follow a company and verify no recruiter access is granted.
11. Retrieve member and company suggestions and verify exclusions.
12. Save a job and close it; verify the safe closed state.
13. Create two overlapping saved searches with alerts enabled.
14. Publish a matching job and verify one alert.
15. Replay its publication event and verify no duplicate.
16. Edit the published job and verify no new alert.
17. Disable or change a search during processing and verify the defined behavior.
18. Hide a matched job before delivery and verify current eligibility handling.
19. Restart a matching worker during a batch and verify recovery.
20. Run MVP-1, MVP-2, and MVP-3 regression tests.

Use real Oracle and Kafka integration tests for persistence, concurrency, and recovery. Mocks must not be the only evidence.

Use isolated test data and condition-based asynchronous waits.

## 15. Progress and completion

Maintain:
- `docs/mvp4-plan.md`
- `docs/mvp4-progress.md`
- `docs/mvp4-verification.md`
- `docs/discovery-and-alert-semantics.md`

Update existing architecture, ADRs, contracts, technology decisions, and runbooks.

At each phase boundary:
- Summarize implemented behavior.
- Record executed checks.
- Separate PASS, FAIL, BLOCKED, and NOT RUN.
- Record unresolved issues and the next executable action.

If interrupted, save a precise checkpoint and resume from repository evidence.

MVP-4 is complete when:
- Following, expanded feeds, saved items, discovery, and job alerts work through real APIs.
- Privacy and company isolation remain intact.
- Matching is bounded, durable, restartable, and deduplicated.
- Existing functionality remains compatible.
- Populated MVP-3 data upgrades successfully.
- Local deployment checks are executed or explicitly marked blocked.
- No required business logic remains as placeholders.

Do not claim machine-learning personalization, production readiness, or internet-scale capacity.

Start by inspecting MVP-3 and running baseline verification. Then implement MVP-4 phase by phase until the acceptance criteria are satisfied.
Paste this into the same IntelliJ Codex project. MVP-7 turns the accumulated platform into a **verified release candidate**, focusing on security, abuse prevention, performance, deployment safety, and disaster recovery.

You are my principal backend engineer and implementation agent working inside IntelliJ.

Advance the existing professional networking platform from MVP-6 to MVP-7.

This release focuses on operational readiness. Implement and verify the controls needed to operate the existing product reliably before adding further product features.

Create actual code, tests, infrastructure configuration, CI workflows, dashboards, and runbooks. Do not deliver only recommendations or a readiness checklist.

## 1. Starting point and execution rules

The intended platform contains:
- Java 21, Spring Boot 4.x, Spring Cloud, Spring Security, and Maven.
- Gateway, member, content, media, messaging, notification, and hiring services.
- Keycloak for OIDC.
- Oracle with service-owned schemas.
- Kafka with transactional outbox publishing and idempotent consumers.
- Private S3-compatible object storage.
- Docker Compose and Kubernetes deployment.
- Prometheus, Grafana, OpenTelemetry, and Zipkin.

Previous releases should provide:
- Social networking, publishing, media, discovery, and saved items.
- Private messaging and live event streams.
- Companies, jobs, applications, and job alerts.
- Moderation, privacy, blocking, and account lifecycle controls.
- Personal-data export and account deletion workflows.

Treat these as intended capabilities, not verified facts.

Before editing:
- Read applicable AGENTS.md instructions.
- Inspect the repository and prior verification records.
- Run available baseline checks.
- Build an evidence-based inventory of implemented and missing capabilities.
- Identify critical dependencies and operating assumptions.
- Preserve unrelated work.

Continue between phases after verification gates pass.

Do not:
- Rewrite services unnecessarily.
- Add infrastructure merely because it appears in the original technology list.
- Weaken tests, security, or delivery guarantees.
- Claim production readiness based only on successful startup.
- Deploy to shared infrastructure, provision paid resources, or run disruptive tests outside an authorized environment.

Default execution targets are Docker Compose and a dedicated local Kubernetes environment.

## 2. MVP-7 scope

Deliver:

1. A measured workload and capacity baseline.
2. Enforceable abuse and resource controls.
3. Service identity and infrastructure access hardening.
4. Safe builds, releases, migrations, and rollback procedures.
5. Actionable monitoring and incident runbooks.
6. Verified backup, restore, and deletion reconciliation.
7. A release assessment with explicit remaining blockers.

Exclude:
- New social or hiring features.
- Advertising, billing, and subscriptions.
- AI ranking and analytics platforms.
- Multi-region active-active deployment.
- A service mesh without a demonstrated requirement.
- Automatic cloud provisioning without an approved target.
- Claims of certification or regulatory compliance.

## 3. Establish a release profile

Create `docs/release-profile.md` containing:
- Target environment.
- Expected workload.
- Data volume.
- Concurrent users and live connections.
- Peak request and event rates.
- Required availability and latency.
- Recovery point and recovery time objectives.
- Hardware and resource limits.
- Relevant cost assumptions.

Use existing project requirements where available.

If requirements are missing:
- Establish clearly labeled engineering test assumptions.
- Separate assumed targets from measured results.
- Continue local implementation and measurement.
- Do not invent contractual service levels or business forecasts.

Do not convert laptop measurements into production capacity claims.

Define release gates as:
- Required and verified.
- Required but blocked.
- Explicitly deferred.
- Not applicable.

A blocked required gate prevents an unconditional release recommendation.

## 4. Critical user journeys and failure boundaries

Identify and test these journeys:
- Login and profile access.
- Feed retrieval.
- Post creation and image access.
- Message send and reconnect.
- Job search and application submission.
- Notification delivery.
- Account export and deletion.

For each journey document:
- Authoritative data owner.
- Synchronous dependencies.
- Asynchronous dependencies.
- Timeout budget.
- Retry policy.
- Idempotency behavior.
- Failure response.
- Recovery mechanism.
- Relevant authorization and lifecycle checks.

Do not turn dependency failures into misleading success.

Examples:
- An unavailable relationship service must not produce an unauthorized feed.
- A Kafka outage may delay notifications while an outbox-backed business transaction succeeds.
- An object-store outage must not produce a successful media-download response.
- A deletion workflow must remain incomplete when required cleanup is unverified.

## 5. Abuse prevention and resource limits

Implement practical controls for:
- Connection and follow requests.
- Posts and comments.
- Message sends.
- Job applications.
- Reports.
- Uploads.
- Export requests.
- Saved-search creation.
- Expensive search queries.
- Live stream connections.

Define limits in versioned configuration.

Use appropriate keys:
- Authenticated member.
- Trusted service identity.
- IP address for limited unauthenticated endpoints.
- Resource or operation where necessary.

Do not trust arbitrary forwarded IP headers. Configure trusted proxy boundaries.

Return predictable errors:
- HTTP 429 for request-rate rejection.
- A meaningful Retry-After value when available.
- Separate validation, authorization, and infrastructure errors.

Layer controls:
- Database constraints for durable business limits.
- Per-instance admission control for resource protection.
- Cluster-wide quotas only where required.

Never describe per-instance counters as global enforcement.

Before adding a distributed rate-limit store:
- Identify the exact global guarantee required.
- Evaluate existing infrastructure.
- Document availability and failure semantics.
- Add a dependency only when justified.

Keep request-rate limiting separate from:
- Upload byte limits.
- Concurrent export limits.
- Connection counts.
- Per-account storage quotas.

Test behavior under concurrency and retries. Legitimate retries must not duplicate business effects.

## 6. Service and infrastructure security

Audit and fix:
- JWT issuer, audience, signature, and expiry validation.
- Resource ownership and company isolation.
- Internal service authentication.
- Overprivileged database credentials.
- Publicly exposed management endpoints.
- Object-store permissions.
- Kafka producer and consumer permissions.
- Secret handling.
- Sensitive logging.

Distinguish:
- Requests carrying an end-user identity.
- Background service operations.
- Operator actions.

Do not use an all-powerful shared service credential.

For the hardened deployment profile:
- Enable supported encrypted transport.
- Configure service-specific database credentials.
- Restrict Kafka access to required topics and consumer groups.
- Keep object buckets private.
- Restrict identity-provider administration.
- Use secret references rather than committed credentials.
- Provide a tested rotation procedure for at least one representative credential.

Kubernetes:
- Use non-root containers where compatible.
- Disable unnecessary privilege escalation.
- Restrict service-account permissions.
- Disable automatic service-account token mounting where unnecessary.
- Add resource requests and limits.
- Apply network policies when the cluster actually supports enforcement.

Do not claim network isolation merely because a NetworkPolicy manifest exists. Verify the selected network implementation enforces it.

Keep local development usable, but clearly distinguish development defaults from the hardened profile.

## 7. Performance and database correctness

Measure before optimizing.

Investigate:
- Slow queries and missing indexes.
- N+1 database queries.
- Excessive cross-service calls.
- Unbounded result sets.
- Large payloads.
- Outbox and consumer throughput.
- Export memory usage.
- Live stream resource consumption.
- Connection pool exhaustion.

Use representative synthetic data with a fixed generation seed.

For Oracle:
- Inspect relevant query plans.
- Test against Oracle, not an in-memory substitute.
- Budget total database connections across all replicas and workers.
- Account for background jobs and migration processes.
- Preserve transaction and authorization semantics during tuning.

For Kafka:
- Measure consumer lag and processing time.
- Verify partition-key choices.
- Identify ordering requirements.
- Do not add partitions without considering ordering and operational effects.

For live streams:
- Measure active-connection memory.
- Verify bounded queues and slow-client handling.
- Test reconnect storms with jitter.
- Keep ordinary REST requests responsive.

Introduce caching only for a measured bottleneck.

Every cache needs:
- Ownership.
- Key design.
- Size bound.
- TTL.
- Invalidation behavior.
- Failure behavior.
- Privacy implications.

Do not let cached state bypass account deletion, blocking, moderation, or authorization.

## 8. Observability and alerting

Provide dashboards for:
- Request traffic, latency, and errors.
- Database pool utilization.
- Outbox backlog and oldest pending record.
- Kafka consumer lag and failures.
- Job-alert processing delay.
- Live stream connections and replay resets.
- Export and deletion workflow progress.
- Storage usage and upload failures.

Define service indicators around user-visible outcomes.

Distinguish:
- Durable message acceptance.
- Event emission.
- Client observation.
- Explicit read state.

Do not treat successful socket writes as confirmed user delivery.

Create actionable alerts:
- Each alert has a symptom, severity, owner role, and runbook.
- Thresholds are justified by the release profile or measured baseline.
- Account for low traffic and brief deployment transitions.
- Avoid alerting on every transient retry.

Verify at least one alert end to end in the disposable environment.

Do not use member IDs, job IDs, or operation IDs as metric labels. Use logs and traces for individual investigations.

## 9. Build and supply-chain controls

Implement CI stages for:
- Formatting and compilation.
- Unit and security tests.
- Oracle/Kafka/object-store integration tests where runners support them.
- API and event compatibility checks.
- Migration verification.
- Dependency and container vulnerability scanning.
- Secret scanning.
- Container builds.
- Software bill of materials generation.

Pin reproducible tool and dependency versions.

Build application images once and promote the same immutable digest between environments.

Do not:
- Use mutable image tags as the release identity.
- Commit real secrets.
- Make the pipeline green by suppressing all scanner findings.
- Claim that an unexecuted hosted pipeline passed.

Document scanner exceptions individually with rationale, owner role, and review date.

Use the repository’s existing CI provider. If none exists, provide a reasonable default plus local equivalent commands.

## 10. Safe deployment and migration

Provide:
- Environment-specific configuration.
- Deployment preflight checks.
- Startup, readiness, and liveness probes.
- Graceful shutdown.
- Bounded rolling-update behavior.
- Migration execution as a controlled deployment step.
- Post-deployment smoke tests.

Database changes:
- Prefer expand-and-contract migration patterns.
- Keep old and new application versions compatible during rolling deployment.
- Separate destructive cleanup from the first rollout.
- Do not assume application rollback reverses a database migration.

Test:
- New application code with the upgraded schema.
- The prior compatible application version with that schema.
- Duplicate migration invocation prevention or safe handling.
- Failure before and after migration completion.

Use a simple rolling deployment unless a more complex strategy is justified.

Do not add a canary controller or GitOps platform solely for this release.

Provide rollback procedures with explicit preconditions and limitations.

## 11. Backup and disaster recovery

Create a concrete backup matrix for:
- Oracle schemas.
- Identity-provider state.
- Object storage.
- Required configuration.
- Deletion ledger.
- Kafka data where retention and recovery requirements justify it.

For each item record:
- Backup method.
- Frequency.
- Retention.
- Access control.
- Restore dependencies.
- Recovery verification.

Do not assume Kafka replay replaces database backups.

Run a restore rehearsal using disposable data.

Before restored data serves traffic:
- Reapply account deletion decisions.
- Reconcile lifecycle state.
- Prevent stale exports from becoming downloadable.
- Validate private object permissions.
- Reconcile pending workflows and outbox state.
- Verify that old events cannot recreate deleted accounts.

Measure observed recovery time and data loss against the stated objectives.

If the objectives are not met, report the gap and implement justified improvements. Do not revise the target silently to match the result.

## 12. Controlled resilience testing

Create bounded failure scenarios for:
- Service process termination.
- Kafka interruption.
- Database interruption.
- Object-store interruption.
- Identity-provider unavailability.
- Consumer restart.
- Lost wake-up signals.
- Slow clients.
- Connection saturation.

Before each test:
- Confirm the disposable target environment.
- Set duration and load limits.
- Define stop conditions.
- Capture baseline health.
- Ensure cleanup restores normal operation.

Do not run uncontrolled network disruption or destructive tests against shared environments.

Verify:
- No false business success.
- No duplicate durable effects.
- No authorization bypass.
- Recovery of committed outbox work.
- Bounded resource use.
- Clear operational signals.

## 13. Development phases

### Phase 0 — Baseline and release profile

Inventory capabilities and run baseline verification.

Deliver the workload assumptions, risk register, release gates, and critical-journey dependency map.

Gate:
- Identify what is verified, missing, and blocked.
- Resolve prerequisites needed by subsequent phases.

### Phase 1 — Security and abuse controls

Implement authorization fixes, resource limits, service credentials, and hardened configuration.

Gate:
- Security regression tests pass.
- Limits work under concurrency.
- Hardened settings are exercised, not merely documented.
- Credential rotation is demonstrated.

### Phase 2 — Performance

Generate representative data, run load tests, and fix measured bottlenecks.

Gate:
- Results are reproducible.
- Resource use remains bounded.
- Privacy and consistency tests still pass.
- Capacity limits are documented.

### Phase 3 — Observability

Implement dashboards, service indicators, alerts, and runbooks.

Gate:
- A controlled failure produces the expected alert.
- The runbook identifies and resolves the test incident.
- Sensitive data is absent from telemetry.

### Phase 4 — Release pipeline

Implement build controls, immutable images, deployment preflight, migration checks, smoke tests, and rollback procedures.

Gate:
- Local equivalent checks pass.
- A rolling update and compatible rollback are demonstrated.
- Unexecuted hosted CI steps are reported honestly.

### Phase 5 — Recovery and resilience

Run backup restoration and controlled failure scenarios.

Gate:
- Restored data preserves access restrictions.
- Deletion reconciliation completes before traffic resumes.
- Recovery measurements are recorded.
- Failed targets remain explicit release blockers.

### Phase 6 — Release candidate assessment

Run the full acceptance suite against the final candidate.

Produce:
- Release manifest with commit and image digests.
- Verification evidence.
- Known limitations.
- Required operating procedures.
- A justified go/no-go assessment.

Do not label the release unconditionally ready while required gates remain blocked.

## 14. End-to-end acceptance

Verify these workflows under the release profile:

1. Register and authenticate disposable members.
2. Create profiles, connect, follow, and publish content.
3. Exercise visibility, blocking, moderation, and media access.
4. Exchange messages across multiple service replicas.
5. Reconnect and recover missed events.
6. Publish a job and submit an idempotent application.
7. Trigger a saved-search alert without duplicates.
8. Exercise rate limits without corrupting business state.
9. Interrupt Kafka and recover committed outbox events.
10. Restart services under load.
11. Perform an export and account deletion.
12. Restore disposable backup data.
13. Reapply deletion decisions before enabling access.
14. Perform a rolling update and supported rollback.
15. Run all previous-release regression suites.

Record failures, fixes, and final results. Do not replace failed assertions with weaker ones merely to complete the release.

## 15. Progress and definition of done

Maintain:
- `docs/mvp7-plan.md`
- `docs/mvp7-progress.md`
- `docs/mvp7-verification.md`
- `docs/release-profile.md`
- `docs/release-readiness.md`
- `docs/runbooks/`

Update existing architecture, ADRs, deployment instructions, and technology decisions.

At each phase boundary:
- Summarize actual improvements.
- Record executed checks.
- Separate PASS, FAIL, BLOCKED, and NOT RUN.
- Identify unresolved risks and the next executable action.

If interrupted, save a precise checkpoint and resume from repository evidence.

MVP-7 is complete when:
- Critical security and resource controls are implemented and tested.
- Performance is measured against an explicit workload.
- Builds and deployments are reproducible.
- Monitoring supports diagnosis and recovery.
- Backup restoration and deletion reconciliation are demonstrated.
- Previous product behavior remains correct.
- The release assessment accurately distinguishes verified readiness from remaining environmental or production requirements.

Do not claim regulatory compliance, high availability, disaster-recovery guarantees, or production capacity beyond the evidence.

Start by inspecting MVP-6 and running baseline verification. Then implement MVP-7 phase by phase until its acceptance criteria are satisfied or remaining external blockers are precisely documented.
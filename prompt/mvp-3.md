You are my principal backend engineer and implementation agent working inside IntelliJ.

Extend the existing professional networking platform from MVP-2 to MVP-3 through incremental, verified development.

Create working code, migrations, tests, API contracts, deployment configuration, and operational documentation. Do not
stop after planning or scaffolding.

## 1. Starting point and working rules

The intended existing platform includes:

- Java 21, Spring Boot 4.x, Spring Cloud, Spring Security, and Maven.
- API gateway, member, content, notification, media, and messaging services.
- Keycloak for OIDC.
- Oracle with service-owned schemas.
- Kafka with transactional outbox publishing and idempotent consumers.
- Private S3-compatible object storage.
- Docker Compose and local Kubernetes.
- Prometheus, Grafana, OpenTelemetry, and Zipkin.

Existing product capabilities should include:

- Profiles and connections.
- Posts, comments, likes, and chronological feeds.
- Post visibility and blocking.
- Images and authorized media delivery.
- One-to-one messaging.
- Content reporting and moderation.

Treat this list as intended context, not evidence of implementation.

Before editing:

- Read applicable AGENTS.md instructions.
- Inspect existing code, architecture decisions, progress records, migrations, and tests.
- Run available baseline verification.
- Identify incomplete prerequisites and existing failures.
- Repair prerequisites required by MVP-3 before adding dependent features.
- Preserve unrelated files and user changes.

Continue automatically between phases after their verification gates pass. Ask only when missing information genuinely
blocks progress.

Do not:

- Rewrite working services unnecessarily.
- Upgrade frameworks without a concrete compatibility or security need.
- Add technologies merely to satisfy a technology checklist.
- Disable security or tests to obtain a passing build.
- Claim execution, deployment, or performance results without evidence.
- Provision paid resources or deploy to shared infrastructure without authorization.

## 2. MVP-3 objective

Deliver a minimal hiring workflow:

1. Members create and manage company pages.
2. Company owners authorize recruiters.
3. Recruiters publish and manage job openings.
4. Members search jobs and submit applications.
5. Recruiters review applications and update their status.
6. Applicants receive notifications and track their applications.

Keep the scope focused.

Exclude:

- Payments, premium subscriptions, sponsored jobs, and advertisements.
- AI matching, resume parsing, candidate scoring, and automated hiring decisions.
- External applicant-tracking integrations.
- Interview scheduling, offers, background checks, and onboarding.
- Resume or document uploads.
- Anonymous applications.
- Company social feeds, follower systems, and company messaging accounts.
- A frontend application.
- GraphQL, gRPC, WebSockets, and new language stacks.

Use existing member profiles as the basis of applications.

## 3. Architecture and service ownership

Add one independently deployable `hiring-service`.

Keep these modules inside hiring-service:

- Companies.
- Company memberships and invitations.
- Job postings.
- Applications.
- Hiring moderation and audit history.

These concepts share authorization and transactional invariants. Do not create separate company, job, applicant, and
recruiter microservices for this release.

Hiring-service owns:

- Company records.
- Company roles and invitations.
- Job records.
- Applications and application snapshots.
- Application status history.
- Relevant audit records.
- Hiring outbox events.

Existing services retain ownership:

- Member-service: member profiles, relationships, and blocking.
- Media-service: company logos and binary lifecycle.
- Notification-service: hiring notifications.
- Gateway: routing and edge controls.

Rules:

- No cross-service database access.
- No shared persistence entities.
- Authenticate internal APIs.
- Use REST for required synchronous interactions.
- Use Kafka for notifications and other asynchronous effects.
- Keep business authorization in hiring-service, not only in the gateway.
- Reuse established technical patterns without creating a large shared business library.

Document service boundaries and cross-service workflows with concise ADRs and sequence diagrams.

## 4. Company pages and roles

Support company pages containing:

- Display name.
- Unique normalized slug.
- Description.
- Industry.
- Location.
- Optional website URL.
- Optional logo managed by media-service.

Validate website URLs, but do not fetch them or generate server-side previews.

Company creation:

- An authenticated member creates a company and becomes its owner.
- Start with one owner per company.
- Do not present self-created companies as verified employers.
- State the absence of employer verification clearly in API/documentation terminology.

Company roles:

- `OWNER`: edit company details, invite/remove recruiters, transfer ownership, and manage jobs/applications.
- `RECRUITER`: manage jobs and applications for that company.
- No member receives organization access solely from an email domain.

Recruiter invitations:

- Invite an existing member by stable member ID.
- Only the invited member can accept.
- Support acceptance, rejection, cancellation, and expiry.
- Prevent duplicate active invitations.
- Do not expose whether arbitrary private email addresses exist.

Ownership:

- Transfer ownership only to an existing accepted company member.
- Perform transfer atomically.
- Prevent an owner from accidentally removing the company’s only owner.
- Define the previous owner’s resulting role.
- Audit role changes and ownership transfers.

Use database constraints and transactions to enforce membership invariants under concurrency.

Do not implement company deletion in this release.

## 5. Job postings

Support:

- Draft creation.
- Editing.
- Publishing.
- Closing.
- Viewing company jobs.
- Viewing a published job.
- Paginated recruiter job management.

Fields:

- Company ID.
- Title.
- Plain-text description.
- Location.
- Work arrangement: onsite, hybrid, or remote.
- Employment type: full-time, part-time, contract, or internship.
- Optional salary minimum, maximum, currency, and pay period.
- Optional application deadline.
- Created, updated, and published timestamps.

Validation:

- Bound all text fields.
- Require salary minimum to be no greater than maximum.
- Use decimal monetary values and explicit currency/pay-period fields.
- Validate deadlines consistently in UTC.
- Do not infer salary currency from a member’s location.

Lifecycle:

- `DRAFT → PUBLISHED → CLOSED`.
- Closed jobs cannot receive applications.
- Reopening is outside MVP-3; create a new posting when needed.
- Drafts are visible only to authorized company members.
- Published jobs are searchable by authenticated members.
- Closed jobs leave search results.
- Existing applicants retain access to a safe job summary and their application history.

Make publish and close operations idempotent.

Do not allow a closing job and a concurrent application submission to violate the application policy. Enforce the
decision transactionally within hiring-service.

Preserve an application-time job snapshot so later edits do not rewrite what an applicant applied to.

## 6. Job search

Implement useful search without adding a search cluster by default.

Support:

- Keywords over title and description.
- Company.
- Location.
- Work arrangement.
- Employment type.
- Stable pagination.
- Newest-published ordering.

Start with Oracle-backed search:

- Define normalization and matching semantics.
- Escape wildcard characters where applicable.
- Use parameterized queries.
- Add indexes for structured filters and ordering.
- Bound query length and page size.
- Explain keyword-search limitations.

Do not claim indexed full-text search when using substring matching.

Only adopt Oracle Text or another search engine if:

- The requirement and measured baseline justify it.
- Availability and compatibility are verified.
- Local development and deployment remain reproducible.
- An ADR describes indexing, freshness, deletion, authorization, and operational costs.

Search must exclude drafts, closed jobs, and moderated jobs.

Do not return misleading total counts if exact counting is too expensive. Prefer a documented cursor and `hasMore`
response.

## 7. Applications

Allow an authenticated member to:

- Apply to a published, open job.
- Include an optional bounded cover note.
- View their applications.
- View application status history.
- Withdraw an active application.

Allow authorized company members to:

- List applications for their company’s jobs.
- Filter by job and status.
- View application details.
- Update application status.

Use these statuses:

- `SUBMITTED`
- `IN_REVIEW`
- `SHORTLISTED`
- `REJECTED`
- `WITHDRAWN`

Define and test a transition matrix:

- Recruiters move active applications through review or shortlist, or reject them.
- Applicants can withdraw any nonterminal application.
- `REJECTED` and `WITHDRAWN` are terminal.
- No reopening or reapplication in MVP-3.

Enforce one application per applicant per job with a database constraint.

Application submission:

- Require a client-generated idempotency key.
- Scope it to the authenticated applicant and submission operation.
- Return the original result for an identical retry.
- Return a conflict when the same key is reused with different input.
- Handle concurrent submissions deterministically.

Profile snapshot:

- Obtain an authorized, versioned snapshot of the applicant’s current professional profile from member-service.
- Include only fields needed for application review.
- Exclude private messages, connection lists, account email, and authentication data.
- Treat the fetched snapshot version as the application-time profile version.
- Do not silently update it when the member later edits their profile.

Capture the job snapshot in the hiring transaction.

If member-service is unavailable during submission, return a retriable failure. Do not create an application with
fabricated or incomplete profile data.

Do not allow current company members to apply to their own company’s jobs in this MVP.

## 8. Application privacy and company isolation

An application is visible only to:

- Its applicant.
- Current authorized members of the hiring company.

Every application query must enforce this policy.

Requirements:

- An applicant cannot retrieve another applicant’s application.
- A recruiter cannot access another company’s applications by guessing identifiers.
- Removing a recruiter revokes access on subsequent requests.
- Do not trust stale company-role claims embedded in a long-lived user token.
- Derive the applicant and acting recruiter from the authenticated identity.
- Do not reveal applicant counts or candidate details to ordinary members.
- Keep cover notes and profile snapshots out of logs, traces, and generic event payloads.

Blocking semantics:

- Member blocking continues to govern personal profiles, connections, content, and messaging.
- Company recruiting access is granted separately through company membership and the applicant’s submission.
- A personal block does not silently delete or hide an application from authorized company reviewers.
- Document this boundary explicitly.
- A recruiter cannot use application access to bypass blocking in messaging-service.

Withdrawal:

- Preserve minimal application and audit history.
- Redact the cover note and profile snapshot from recruiter-facing responses after withdrawal.
- Explain that previously viewed information cannot be retroactively revoked.
- Define retention separately from API visibility; do not claim immediate erasure.

Document retention as a configurable product/operational policy, not a claim of legal compliance.

## 9. Notifications and events

Add notifications for:

- A company recruiter invitation.
- A new application.
- An application status change.

Use the established outbox and deduplication patterns.

Events should contain identifiers and the minimum routing information required. Do not include cover notes or full
profile snapshots.

Rules:

- No notification for a no-op status update.
- Duplicate events must not create duplicate notifications.
- Do not notify an actor about their own action unnecessarily.
- Resolve current company recipients through an authorized internal interface.
- If recipient resolution fails, retry rather than silently dropping the notification.
- Require current authorization when opening notification targets.
- Keep recruiter notifications generic so removed recruiters do not retain candidate details.

Document how membership changes between an event and its consumption affect recipients.

Preserve at-least-once delivery semantics; do not claim end-to-end exactly-once behavior.

## 10. Basic hiring moderation

Add reporting of published jobs for:

- Spam.
- Suspected fraud.
- Inappropriate content.
- Other, with a bounded explanation.

Implement inside hiring-service:

- One active report per reporter and job.
- Moderator-only report queue.
- Dismiss report.
- Hide and restore a job.
- Audited moderator actions.

Keep moderation status separate from job lifecycle:

- Hiding a job removes it from search and prevents new applications.
- Existing applicants and authorized recruiters retain permitted application access.
- Restoring visibility does not reopen a closed job or override its deadline.
- Ordinary members cannot identify reporters.
- Recruiters cannot grant themselves platform moderator permissions.

Do not implement automated fraud detection or claim verified employer identity.

## 11. Data correctness and compatibility

Use:

- Versioned Oracle migrations.
- Database uniqueness and referential constraints within hiring-service.
- Optimistic locking or explicit locking for competing state transitions.
- UTC timestamps.
- Stable external identifiers.
- Deterministic pagination.
- Bounded queries.

Test:

- Two recruiters changing the same application concurrently.
- Application submission racing with job closure.
- Duplicate submissions.
- Concurrent invitation acceptance and cancellation.
- Ownership transfer racing with membership removal.

Preserve existing MVP-1 and MVP-2 APIs.

Test migrations:

- From an empty database.
- From a populated MVP-2 database.

Avoid destructive schema changes. Explain rollback limits and use additive migrations where possible.

## 12. Technology discipline

Keep Java 21 and the established Spring stack.

Use MVC and JPA in hiring-service unless the existing architecture provides a justified alternative.

Do not add Scala, Play, Rest.li, Spark, Hadoop, HDFS, Pinot, Samza, Espresso, Voldemort, Couchbase, Helix, Memcached,
Ruby, C++, or Akamai simply because they appeared in the original technology list.

Evaluate Spring Batch only if a genuine restartable maintenance or retention job warrants it. Deadline enforcement must
work at request time even if a scheduled job is delayed.

Update technology decisions with:

- New dependencies and pinned versions.
- Compatibility evidence.
- Alternatives considered.
- Deferred technologies and adoption triggers.

## 13. Implementation phases

### Phase 0 — Baseline and design

Deliver:

- MVP-2 verification results.
- MVP-3 acceptance checklist.
- Company authorization matrix.
- Job and application state machines.
- Data model and API/event contracts.
- Migration plan.
- Privacy and retention decisions.

Gate:

- Resolve prerequisites needed by the next phase.
- Record remaining unrelated baseline failures separately.

### Phase 1 — Companies and recruiter membership

Implement:

- Hiring-service foundation.
- Company CRUD within scope.
- Logo integration.
- Recruiter invitations.
- Role enforcement.
- Ownership transfer and audit history.

Gate:

- Company roles cannot cross company boundaries.
- Membership operations remain correct under concurrency.
- Removed recruiters lose access.
- Existing media authorization patterns protect company assets.

### Phase 2 — Jobs and search

Implement:

- Draft, publish, edit, and close workflows.
- Validation and deadlines.
- Search and pagination.
- Appropriate database indexes.

Gate:

- Unauthorized members cannot manage company jobs.
- Drafts and closed jobs stay out of search.
- Publishing and closing are idempotent.
- Search behavior and performance are measured and documented.

### Phase 3 — Applications

Implement:

- Idempotent submission.
- Profile and job snapshots.
- Applicant and recruiter queries.
- Status transitions.
- Withdrawal and response redaction.

Gate:

- Duplicate requests create one application.
- Job closure and application submission obey a tested concurrency policy.
- Company isolation holds.
- Snapshots remain stable after profile or job edits.
- Invalid and conflicting transitions return predictable errors.

### Phase 4 — Events and moderation

Implement:

- Hiring notifications.
- Recipient resolution.
- Report queue and moderator actions.
- Safe handling of inaccessible notification targets.

Gate:

- Replay does not duplicate notifications.
- Dependency failures retry safely.
- Hidden jobs cannot receive new applications.
- Restoration preserves lifecycle and deadline restrictions.
- Moderator actions are authorized and audited.

### Phase 5 — Hardening and performance

Implement:

- Security regression tests.
- Oracle and Kafka integration tests.
- Contract tests.
- Load tests for search, application submission, and recruiter listings.
- Relevant metrics, dashboards, and traces.

Gate:

- Failures do not cause lost committed applications or events.
- Query behavior avoids unbounded N+1 calls.
- Logs and traces contain no application content.
- Report latency and throughput with hardware, dataset, concurrency, and duration.
- Fix observed bottlenecks before introducing caching or new infrastructure.

### Phase 6 — Deployment and release

Update:

- Docker Compose.
- Container builds.
- Kubernetes configuration.
- Database migration execution.
- CI checks.
- Backup/restore and rollback procedures.
- IntelliJ HTTP requests.
- End-to-end scripts.
- Upgrade documentation.

Gate:

- Fresh local installation works.
- Upgrade from populated MVP-2 data works.
- Deployed end-to-end tests pass.
- Application restarts preserve company, job, and application state.
- Unexecuted deployment checks are reported explicitly.

## 14. End-to-end acceptance scenario

Automate this journey through the gateway:

1. Create an owner, recruiter, two applicants, an unrelated company owner, and a moderator.
2. Create a company and attach its logo.
3. Invite the recruiter and accept the invitation.
4. Create and publish a job.
5. Find the job through search filters.
6. Submit an application with a profile snapshot.
7. Retry the submission and verify that only one application exists.
8. Edit the applicant’s profile and verify the application snapshot remains unchanged.
9. Move the application to review and then shortlist.
10. Verify applicant notifications.
11. Attempt access from the unrelated company and verify denial.
12. Withdraw the application and verify recruiter-facing redaction.
13. Race another application submission against job closure and verify the defined outcome.
14. Remove the recruiter and verify access revocation.
15. Report and hide another published job.
16. Verify it disappears from search and rejects new applications.
17. Restore it and verify its lifecycle/deadline policy still applies.
18. Replay events and verify notification deduplication.
19. Restart services and verify persisted state.
20. Run existing MVP-1 and MVP-2 regression tests.

Use real Oracle and Kafka integration tests. Mocks must not be the only evidence for cross-service behavior.

Use deterministic condition-based waits and isolated test data.

## 15. Progress and documentation

Maintain:

- `docs/mvp3-plan.md`
- `docs/mvp3-progress.md`
- `docs/mvp3-verification.md`
- `docs/hiring-authorization-matrix.md`

Update existing architecture, ADRs, API documentation, technology decisions, and operational runbooks.

At every phase boundary:

- Summarize implemented behavior.
- Record actual verification commands and results.
- Separate PASS, FAIL, BLOCKED, and NOT RUN.
- List unresolved issues.
- Identify the next executable action.

If interrupted:

- Save a precise checkpoint.
- Record the current phase and incomplete acceptance criteria.
- Resume from repository evidence instead of recreating completed work.

## 16. Definition of done

MVP-3 is complete when:

- Companies, recruiter membership, jobs, search, applications, and hiring moderation work through real APIs.
- Company and applicant authorization are tested.
- Concurrency constraints and idempotency are verified.
- Application snapshots preserve submission-time information.
- Notifications recover safely from failures and duplicate delivery.
- Existing release regression tests pass.
- A populated MVP-2 deployment can be upgraded.
- Local deployment and operational instructions match executed checks.
- Required business logic contains no placeholders.
- Remaining blockers and limitations are reported honestly.

Do not claim production readiness, employer verification, legal compliance, or internet-scale capacity without
supporting evidence.

Start by inspecting MVP-2 and running baseline verification. Then implement MVP-3 in phases until the acceptance
criteria are satisfied.
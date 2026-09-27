You are my principal backend engineer and implementation agent working inside IntelliJ.

Build a complete, runnable backend for a minimal professional networking platform inspired by LinkedIn. Work through
architecture, implementation, testing, containerization, and local Kubernetes deployment in verified phases.

Create actual project files and execute available checks. Do not stop after producing an architecture proposal, code
snippets, or scaffolding.

## 1. Objective and working rules

Project name: `professional-network-mvp`.

Use Java 21 and Spring Boot 4.x as the primary implementation stack. Deliver a small microservice system with clear
ownership and independent deployment.

Optimize for correctness, maintainability, understandable code, and reproducible local execution. This is an MVP, not a
reconstruction of LinkedIn’s internal infrastructure.

Before editing:

- Read applicable AGENTS.md instructions and inspect the existing workspace.
- Preserve unrelated files and existing work.
- Identify available Java, Maven, Docker, Kubernetes, and network access.
- If an existing project is present, extend it rather than creating a competing structure.

Execution:

- Make reasonable implementation decisions and record material assumptions.
- Ask questions only when missing information genuinely blocks progress.
- Continue automatically between phases after their verification gates pass.
- Fix failures before adding dependent features.
- Never disable security, weaken assertions, or skip failing tests to claim success.
- Do not fabricate dependency versions, library APIs, test results, or deployment status.
- When a tool or dependency is unavailable, record the exact blocker and continue independent work.
- Respect environment permission boundaries.
- Do not provision paid infrastructure, publish externally, or deploy to a shared cluster without authorization.
- Default deployment targets are Docker Compose and a dedicated local kind cluster.

## 2. MVP-1 product scope

Implement backend APIs for these capabilities:

Identity:

- User registration, login, token refresh, and logout through an established OIDC provider.
- Use a locally containerized Keycloak instance unless the repository already provides an appropriate identity provider.
- Configure reproducible development realm/client setup.
- Use Authorization Code with PKCE for interactive clients.
- Keep credential management in the identity provider.
- Document logout and access-token expiry semantics.

Member profiles:

- Create and update my profile.
- View another member’s profile.
- Fields: display name, headline, summary, location, and a small list of professional experience entries.
- Search members by name or headline with bounded pagination.
- Keep account email and credential-related information out of public profile responses.

Connections:

- Send, accept, reject, and cancel connection requests.
- List pending requests and accepted connections.
- Remove an accepted connection.
- Prevent self-connections and duplicate relationships.
- Define deterministic behavior for reciprocal requests and concurrent acceptance.

Content:

- Create, edit, and delete my text-only posts.
- View a member’s posts.
- Like and unlike a post.
- Add and delete my comments.
- No nested comments.
- A chronological home feed containing my posts and posts from accepted connections.

Notifications:

- Persist in-app notifications for connection requests, accepted requests, and likes/comments on my posts.
- List my notifications and mark them as read.
- Use polling APIs; no WebSocket requirement.
- Suppress notifications for my own actions on my own content.

Visibility:

- All profiles and posts are visible to authenticated members.
- Connections control home-feed inclusion, not post authorization.
- Document this explicitly.
- No anonymous browsing or configurable privacy settings in MVP-1.

Exclude:

- Direct messaging, jobs, company pages, endorsements, recommendations, subscriptions, advertisements, media uploads, AI
  features, ranked feeds, email delivery, and full frontend development.
- Account deletion and advanced moderation are roadmap items; document their implications.
- Do not scrape LinkedIn or copy its branding, assets, or private APIs.

## 3. Architecture and service ownership

Use a Maven monorepo with these independently deployable applications:

`api-gateway`

- Spring Cloud Gateway using WebFlux.
- Routing, request correlation, bounded request sizes, CORS, and authentication integration.
- No business logic or database access.

`member-service`

- Profiles, experience entries, member search, connection requests, and accepted connections.
- Owns all member and relationship tables.
- Publishes connection domain events.

`content-service`

- Posts, comments, likes, and chronological feed queries.
- Owns all content tables.
- Publishes content interaction events.
- Obtains accepted connection IDs from member-service through an authenticated internal API.
- Never reads member-service tables directly.

`notification-service`

- Consumes relevant domain events.
- Owns notifications and consumer deduplication records.
- Exposes notification queries and mark-read operations.

Keycloak is infrastructure, not a custom authentication microservice.

Rules:

- Do not split likes, comments, search, or connections into additional services.
- Use synchronous REST for required request/response interactions and Kafka for asynchronous events.
- No distributed transactions or cross-service database joins.
- Use service-owned Oracle schemas and separate credentials.
- One Oracle instance may host the schemas locally; document that it remains shared infrastructure.
- Shared modules may contain narrowly scoped technical utilities or contracts, never shared persistence entities or
  domain repositories.
- Organize each service by feature with clear domain, application, and adapter responsibilities. Avoid interfaces and
  layers that add no useful boundary.

Create architecture diagrams and ADRs covering service boundaries, identity, database ownership, feed design, event
reliability, and deployment.

## 4. Technology policy

Required in MVP-1:

- Java 21.
- A verified stable Spring Boot 4.x release.
- A compatible Spring Cloud release train managed through its BOM.
- Spring Security.
- Spring Web MVC for business services.
- Spring WebFlux for the gateway.
- Spring Data JPA/Hibernate for Oracle persistence.
- Spring Kafka and relevant Spring Messaging abstractions.
- Oracle JDBC and versioned database migrations.
- Apache Kafka.
- Maven Wrapper.
- Docker and Docker Compose.
- Kubernetes with a local kind deployment.
- Prometheus, Grafana, and OpenTelemetry.
- Zipkin as the trace backend.
- Structured JSON application logs.

Supporting additions:

- Keycloak for OIDC.
- JUnit, Mockito where useful, Testcontainers, and an API integration-test library.
- A migration tool with verified support for the selected Oracle version.
- Add other dependencies only for a concrete requirement and explain material additions.

Use each web stack intentionally:

- Keep blocking JPA work in MVC business services.
- Keep the reactive gateway free of blocking persistence.
- Do not introduce both server stacks into a service without an explicit, tested reason.

Create `docs/technology-decisions.md` covering every requested technology, including:

- Spring Batch: deferred until a real restartable bulk or scheduled processing requirement exists.
- Spring Integration: deferred until a concrete adapter or integration flow warrants it.
- Scala and Play Framework: optional future service experiment, not a second MVP backend stack.
- Rest.li: optional isolated compatibility experiment; verify maintenance status and Java/build compatibility first.
- gRPC: deferred until a measured internal communication need.
- GraphQL: deferred until client query requirements justify an edge layer.
- JavaScript and Node.js: optional test/load tooling only when needed.
- Python: optional development tooling only when needed.
- Ruby and C++: deferred; no MVP requirement.
- Spark, Hadoop, and HDFS: deferred offline analytics.
- Samza: deferred stream-processing experiment.
- Pinot: deferred real-time analytics.
- Espresso, Voldemort, and Couchbase: deferred storage evaluation.
- Apache Helix: deferred cluster-coordination evaluation.
- Memcached: deferred until measurements justify caching.
- Nginx: optional reverse proxy only if needed; avoid duplicating gateway responsibilities.
- Akamai: future external CDN/edge integration requiring an account and an actual use case.
- Atlas: clarify the exact product and availability before proposing integration.
- Brave: do not add a second tracing instrumentation path alongside OpenTelemetry without need.
- Elasticsearch, Logstash, and Kibana: optional observability profile after the core application works.

For each item record:

- MVP status.
- Purpose and justification.
- Compatibility or availability concerns.
- Trigger for adoption.

Do not silently drop requested technologies or add unused dependencies to imply they are implemented. Do not claim that
a similarly named public project is LinkedIn’s internal system.

At Phase 0, verify exact framework, driver, plugin, and container versions against official documentation and artifact
repositories. Pin reproducible versions; avoid `latest`, snapshots, and dynamic version ranges. Record verification
dates and sources.

If a dependency is incompatible with Boot 4.x, document the conflict and select a compatible approach without silently
downgrading the required stack.

## 5. Data and API requirements

Use versioned REST APIs under `/api/v1`.

Provide:

- OpenAPI contracts.
- Request and response DTOs separate from persistence entities.
- Bean validation and consistent RFC 9457 Problem Details errors.
- UTC timestamps.
- Stable external identifiers.
- Bounded pagination and deterministic ordering.
- Appropriate indexes, unique constraints, foreign keys within a service, and optimistic locking where needed.
- Forward-compatible migration practices.

Persist relationships with a canonical member-pair representation or equivalent database constraint that prevents
reciprocal duplicates under concurrency.

Enforce one like per member per post at the database level.

Define behavior for:

- Repeated like/unlike requests.
- Repeated mark-read requests.
- Duplicate connection commands.
- Editing or commenting on a deleted post.
- Post deletion and associated comments/likes.
- Empty feeds and deleted content.
- Connection removal and feed visibility.

Use a stable cursor such as `(createdAt, id)` for chronological feeds. Avoid one downstream call per post.

Implement feed retrieval using current accepted connections and indexed content queries. Handle connection pagination
and Oracle query limits explicitly. Do not silently truncate connections. If a product limit is needed for the MVP,
document and enforce it in the API.

If member-service is unavailable, return a documented retriable feed error rather than pretending a partial feed is
complete.

Use bulk member lookup where display information is needed.

## 6. Security

Every business service must independently validate tokens and enforce authorization.

Requirements:

- Verify JWT signature, issuer, audience, expiry, and required claims.
- Derive the acting member from the authenticated subject, never a trusted request-body user ID.
- Use a stable mapping from OIDC subject to member profile.
- Make profile initialization idempotent.
- Check ownership on every modifying endpoint.
- Ensure one user cannot read or change another user’s notifications.
- Authenticate and authorize internal service APIs.
- Strip or ignore untrusted identity headers.
- Use least-privilege database credentials.
- Keep secrets outside source control.
- Provide `.env.example` with placeholders and safe local setup instructions.
- Use explicit CORS origins and explain CSRF decisions for the selected authentication model.
- Keep tokens, passwords, and sensitive personal information out of logs and traces.
- Restrict management endpoints.
- Document TLS termination and the difference between local and production configuration.

Include tests for unauthorized access, expired/invalid tokens, ownership violations, and forged user identifiers.

Do not implement custom cryptography or a homegrown authorization server.

## 7. Reliable events and failure handling

Use a transactional outbox in services that publish events:

- Write the domain change and outbox record in the same Oracle transaction.
- Publish through a retryable relay.
- Mark delivery only after broker acknowledgement.
- Handle concurrent relay instances safely.
- Accept at-least-once delivery and make consumers idempotent.

Use an event envelope containing:

- `eventId`
- `eventType`
- `schemaVersion`
- `occurredAt`
- `aggregateId`
- `aggregateVersion` where ordering matters
- `producer`
- Correlation and causation identifiers
- Payload

Do not include access tokens or unnecessary personal data.

For consumers:

- Commit the notification write and deduplication record atomically.
- Acknowledge Kafka processing only after persistence succeeds.
- Define bounded retries, backoff, and dead-letter handling.
- Provide an operational replay procedure.
- Choose message keys for the ordering actually required.
- Explain behavior for duplicates and stale events.
- Do not claim end-to-end exactly-once processing.

Use timeouts for synchronous calls and retry only operations that are safe to retry.

Verify that a Kafka outage does not roll back an otherwise valid business operation after its outbox transaction
commits. Events must be delivered after recovery.

Avoid requiring live synchronous lookups during notification consumption when the event can safely carry the necessary
recipient information.

## 8. Observability

Implement:

- Spring Boot Actuator health endpoints.
- Separate liveness and readiness semantics.
- Prometheus metrics and useful Grafana dashboards.
- OpenTelemetry traces across HTTP and Kafka.
- Structured logs with trace and correlation IDs.
- Metrics for request latency/errors, database pools, outbox backlog, consumer failures, and notification processing.

Do not use user IDs, event IDs, or workflow IDs as metric labels.

Include a demonstrated trace for a request crossing the gateway and a business service, plus trace-context propagation
through an asynchronous notification flow.

Keep basic application startup possible without the full optional observability stack.

## 9. Repository deliverables

Create an understandable layout containing:

- Root Maven build and Maven Wrapper.
- Independent service modules.
- API and event contracts.
- Service-owned migrations.
- Dockerfiles.
- Docker Compose configuration.
- Keycloak development configuration.
- Kubernetes manifests or a small Helm chart.
- Environment examples.
- Local setup and smoke-test scripts.
- IntelliJ HTTP client `.http` request files.
- Unit, integration, contract, and end-to-end tests.
- CI workflow.
- README and operational documentation.

Maintain:

- `docs/architecture.md`
- `docs/adr/`
- `docs/technology-decisions.md`
- `docs/implementation-plan.md`
- `docs/progress.md`
- `docs/verification.md`
- `docs/runbook.md`
- `docs/roadmap.md`

Keep these documents concise and aligned with the actual implementation. Do not create extensive speculative
documentation.

## 10. Phased implementation

### Phase 0 — Discovery and architecture

Inspect the workspace, verify compatibility, establish scope, and document decisions.

Deliver:

- Architecture and service/data ownership diagrams.
- Core entity model and relationship state machine.
- Initial API/event contracts.
- Version and technology decision matrix.
- Ordered implementation plan with acceptance criteria.
- Local hardware and runtime assumptions.

Gate:

- Resolve critical version and infrastructure compatibility issues.
- Make assumptions explicit.
- Continue into implementation without waiting for routine plan approval.

### Phase 1 — Runnable foundation and authentication

Implement:

- Maven modules and wrapper.
- Service startup and health checks.
- Oracle schemas and migration infrastructure.
- Kafka and identity provider in Compose.
- Gateway routing.
- Security configuration.
- An authenticated “create/get my profile” vertical slice.

Gate:

- All modules compile.
- Required infrastructure becomes healthy.
- Migrations run against Oracle.
- A real authenticated request passes through the gateway.
- An unauthenticated protected request is rejected.
- The documented interactive login/token workflow is usable.

### Phase 2 — Profiles and connections

Implement:

- Profile CRUD within the defined scope.
- Member search.
- Connection state transitions.
- Authorization, pagination, constraints, and concurrency handling.

Gate:

- Two users can create profiles and establish a connection.
- Unauthorized modification fails.
- Duplicate and reciprocal requests cannot create duplicate relationships.
- Integration tests use the real database engine.

### Phase 3 — Content and feed

Implement:

- Posts, comments, likes, and member post listings.
- Chronological connection-based feed.
- Authenticated member-service integration.
- Pagination and relevant query optimization.

Gate:

- Connected users see the expected feed.
- Removing a connection affects subsequent feed requests.
- Repeated likes do not duplicate records.
- Content ownership rules hold.
- Pagination is deterministic.
- Dependency failures produce the documented response.

### Phase 4 — Events and notifications

Implement:

- Outbox relay and Kafka event publishing.
- Notification consumers and APIs.
- Deduplication, retries, dead-letter handling, and replay documentation.

Gate:

- Connection and content interactions generate the expected notifications.
- Duplicate events do not create duplicate notifications.
- A broker outage preserves events for later delivery.
- A consumer restart does not lose committed notifications.
- Two relay instances do not invalidate delivery guarantees.

### Phase 5 — Operational hardening

Implement:

- Metrics, dashboards, tracing, and structured logs.
- Complete authorization regression tests.
- Contract and failure-path tests.
- A reproducible load-test script.
- Dependency and container scanning in CI.

Gate:

- Demonstrate end-to-end trace propagation.
- Run the full verification suite.
- Report measured throughput and latency with hardware, dataset, concurrency, and test duration.
- Clearly distinguish measured results from capacity assumptions.
- Fix actionable findings and document remaining limitations.

### Phase 6 — Deployment and handover

Implement:

- Container builds.
- Repeatable Compose startup.
- Kubernetes deployments, services, configuration, secret references, resource requests/limits, and health probes.
- Dedicated migration execution.
- Graceful shutdown.
- Persistence configuration for local infrastructure.
- CI steps for verification and image builds.
- Deployment, backup/restore, and rollback instructions.

Deploy to a dedicated local kind cluster when available.

Gate:

- Run the end-to-end smoke flow against the deployed system.
- Verify rolling restart behavior for application services.
- Demonstrate data persistence across application restarts.
- Validate backup/restore on disposable local data when infrastructure permits.
- Validate manifests even if a cluster is unavailable, but report deployment as unverified.
- Distinguish application rollback from potentially irreversible schema changes.

Do not describe a single-node local Kafka or Oracle deployment as production high availability.

## 11. Testing requirements

Use:

- Unit tests for domain rules and state transitions.
- MVC/security tests for validation and authorization.
- Oracle-backed integration tests for repositories and migrations.
- Kafka-backed integration tests for asynchronous behavior.
- Contract checks for APIs and event schemas.
- End-to-end tests against running services.

Use compatible Testcontainers modules where available. If Oracle container support is unavailable on the host
architecture, use a documented external Oracle test connection or report the blocker. Do not silently substitute H2 and
claim Oracle compatibility.

Keep tests deterministic:

- Use isolated test data.
- Use condition-based waits for asynchronous behavior.
- Avoid arbitrary sleeps.
- Exercise actual failure modes and concurrency invariants.
- Mocks must not be the only evidence for cross-service workflows.

Critical end-to-end acceptance scenario:

1. Create two test identities through a supported test setup.
2. Authenticate both users.
3. Create their profiles.
4. Send and accept a connection request.
5. Create a post.
6. Verify it appears in the connected user’s feed.
7. Like and comment on it.
8. Verify the author receives the expected notifications.
9. Replay an event and verify no duplicate notification.
10. Verify a third user cannot modify that content or read those notifications.
11. Remove the connection and verify feed behavior.
12. Restart application services and verify persisted state.

Provide executable commands and reusable IntelliJ HTTP requests for the same journey.

## 12. Progress, evidence, and resumption

After each phase:

- Summarize implemented behavior.
- Record changed modules.
- Record commands run and actual outcomes.
- Separate PASS, FAIL, BLOCKED, and NOT RUN.
- List unresolved risks and the next phase.

Update `docs/progress.md` with:

- Current phase.
- Completed acceptance criteria.
- Outstanding work.
- Exact blockers.
- Next executable action.

If the session is interrupted or context becomes limited:

- Save a precise checkpoint.
- Leave the repository in the best recoverable state possible.
- On resumption, read the checkpoint and inspect current files.
- Continue from the first incomplete acceptance criterion instead of regenerating completed work.

Do not mark a phase verified if its required checks could not run.

## 13. Definition of done

MVP-1 is complete when:

- All scoped features work through real backend APIs.
- Services have clear data ownership and independent deployable artifacts.
- Authentication and resource authorization are tested.
- Oracle migrations work.
- Kafka processing demonstrates recovery and deduplication.
- A clean checkout can follow the documented setup successfully.
- Compose startup and the end-to-end smoke test pass.
- Local Kubernetes deployment is verified, or explicitly reported as blocked.
- CI configuration exists and local equivalent checks pass.
- Operational documentation reflects actual commands and limitations.
- No placeholder business logic remains in required features.

Do not claim production readiness or internet-scale capacity.

Start now with Phase 0, create the implementation plan, and proceed through the phases. Make the codebase runnable
incrementally and use verification evidence to determine completion.
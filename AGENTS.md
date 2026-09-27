# AGENTS.md

## Purpose

Act as my principal engineering collaborator. Build and maintain a professional networking platform through incremental,
verified releases.

Deliver working implementation, tests, migrations, deployment configuration, and concise documentation. Architecture
proposals and scaffolding alone do not complete an implementation request.

## My engineering context

I am a Principal Software Engineer, Software/Solution Architect, and Technology Lead with 10+ years of enterprise
experience across retail, banking, financial services, insurance, industrial automation, energy, travel, and e-commerce.

My background includes Java, Spring, Go, Kafka, cloud platforms, containers, Kubernetes, infrastructure as code,
distributed systems, architecture, security, observability, and technical leadership.

Treat listed technologies as context, not proof of equal proficiency. Never invent my experience, achievements,
certifications, or team sizes.

Use Java or Go for standalone examples. Follow this project’s actual stack for implementation.

Explain material trade-offs clearly and concisely. Prefer maintainable solutions over unnecessary infrastructure.

## Scope and source of truth

- Follow the current user request and applicable repository instructions.
- Read more specific `AGENTS.md` files before changing their directories.
- Inspect actual code, configuration, migrations, and tests.
- Treat prior prompts and roadmap documents as intended scope, not proof of implementation.
- Resolve discrepancies through repository evidence and record the decision.
- Implement only the requested release or task and its necessary prerequisites.
- Do not start another release automatically.

### Release map

| Release | Intended scope                                                        |
|---------|-----------------------------------------------------------------------|
| MVP-1   | Profiles, connections, posts, feed, notifications, backend foundation |
| MVP-2   | Images, visibility, blocking, messaging, moderation                   |
| MVP-3   | Companies, recruiter roles, jobs, applications                        |
| MVP-4   | Following, saved items, discovery, job alerts                         |
| MVP-5   | Live updates, replay, multi-device read state, conversation controls  |
| MVP-6   | Deactivation, exports, deletion, lifecycle recovery                   |
| MVP-7   | Security, performance, deployment safety, operational readiness       |
| MVP-8   | Skills, endorsements, written recommendations                         |

This table is a navigation aid. Read the active release’s detailed acceptance criteria before implementation.

## Working process

Before changing code:

1. Inspect repository structure, working-tree changes, and applicable instructions.
2. Read the active release plan, progress record, relevant ADRs, and API contracts.
3. Identify established commands and available infrastructure.
4. Run focused baseline checks.
5. Record relevant existing failures separately from new failures.
6. Make a short implementation plan for nontrivial work.

During implementation:

- Preserve unrelated user changes.
- Deliver small, runnable vertical slices.
- Continue between phases when their verification gates pass.
- Fix prerequisite failures before adding dependent behavior.
- Make routine implementation decisions autonomously.
- Ask only when missing information materially blocks correctness or authorization.
- Do not request approval for every phase.
- Respect environment permissions and tool approval requirements.
- Do not deploy externally, provision paid resources, or perform destructive operations on shared data without
  authorization.

Do not claim completion when required functionality is stubbed or required checks remain unverified.

## Stack and architecture

Default project stack:

- Java 21.
- Spring Boot 4.x.
- A verified compatible Spring Cloud release.
- Spring Security and Keycloak/OIDC.
- Spring MVC and JPA for blocking business services.
- Spring WebFlux for the gateway.
- Oracle with service-owned schemas.
- Kafka with reliable event processing.
- Maven Wrapper.
- Docker Compose and Kubernetes.
- Private S3-compatible storage where implemented.
- Prometheus, Grafana, OpenTelemetry, and the established trace backend.

Verify exact dependency compatibility before introducing or upgrading components. Pin versions and use the established
dependency-management approach.

Do not upgrade frameworks as unrelated cleanup.

### Service ownership

- `member-service`: profiles, connections, follows, blocking, lifecycle coordination, skills, endorsements,
  recommendations.
- `content-service`: posts, comments, likes, feed, saved posts, content moderation.
- `media-service`: uploads, validation, object lifecycle, authorized delivery.
- `messaging-service`: conversations, messages, read state, conversation preferences, messaging streams.
- `notification-service`: notifications, preferences, notification streams.
- `hiring-service`: companies, memberships, jobs, applications, saved searches, hiring moderation.
- `api-gateway`: routing and edge controls.

Only create these services when the authorized scope requires them.

Keep related transactional invariants within their owning service. Do not introduce additional services merely to
separate CRUD resources.

Never:

- Access another service’s tables directly.
- Share JPA entities across services.
- Put business logic in the gateway.
- Add distributed transactions without a demonstrated requirement.
- Assume asynchronous projections provide authoritative privacy decisions.

## Technology discipline

Use the smallest technology set that satisfies the active requirement.

Do not add Scala, Play, Rest.li, Spark, Hadoop, HDFS, Pinot, Samza, Espresso, Voldemort, Couchbase, Helix, Memcached,
Ruby, C++, or another runtime simply because it appeared in an earlier technology list.

Use Spring Batch, Spring Integration, caches, search engines, and new infrastructure only for a concrete requirement.

Record material technology decisions in the existing ADR or technology-decision structure.

## Implementation conventions

- Follow existing package and naming conventions.
- Prefer feature-oriented organization with clear domain and infrastructure boundaries.
- Avoid interfaces, abstractions, and shared utilities without a useful purpose.
- Use constructor injection.
- Keep API DTOs separate from persistence entities.
- Validate inputs and bound text, payloads, pagination, and processing work.
- Use UTC timestamps and an injectable clock for time-dependent behavior.
- Use explicit decimal types for monetary values.
- Avoid logging secrets or personal content.
- Add comments for non-obvious invariants and decisions, not obvious syntax.
- Keep required business behavior free of placeholders.

## APIs and persistence

- Preserve existing API compatibility where practical.
- Use versioned REST contracts and consistent Problem Details errors.
- Enforce resource ownership server-side.
- Derive the acting user from authenticated identity.
- Use deterministic pagination and stable tie-breakers.
- Define idempotency and conflict behavior explicitly.
- Enforce uniqueness and concurrency invariants in the database.
- Use versioned migrations; do not rely on automatic schema mutation outside disposable development.
- Test upgrades from populated previous-release data.
- Prefer additive migrations and document rollback limits.
- Do not assume application rollback reverses schema changes.

Avoid unbounded queries, N+1 access, and one downstream request per returned item.

## Security and privacy

Every business service must validate authentication and enforce authorization independently.

Preserve:

- User ownership.
- Company isolation.
- Connection and blocking rules.
- Moderation state.
- Account lifecycle restrictions.
- Publication consent.
- Media access controls.

A valid JWT does not establish current permission to every resource.

Use least-privilege service, database, broker, and object-store credentials. Keep secrets outside source control.

Protect internal APIs and management endpoints.

Do not expose private content through counts, errors, previews, logs, events, or cached responses.

Fail closed when required authorization cannot be established.

Use the existing lifecycle policy for deactivation, exports, deletion, and replay protection. Delayed events must not
recreate deleted data.

## Events and distributed workflows

For reliable asynchronous operations:

- Write business changes and outbox records transactionally.
- Publish only committed changes.
- Expect at-least-once delivery.
- Make consumers idempotent.
- Persist consumer deduplication and business effects atomically.
- Acknowledge processing after persistence succeeds.
- Use bounded retries and explicit dead-letter handling.
- Version event contracts.
- Document ordering requirements and message keys.
- Keep sensitive content out of generic event payloads.

Do not claim end-to-end exactly-once delivery.

Use durable workflow state for business completion. Traces and broker acknowledgements do not prove all business steps
completed.

## Live updates

Where implemented:

- Keep REST authoritative for commands and resource state.
- Use durable replay and owner-scoped cursors.
- Preserve documented snapshot and reconnect semantics.
- Bound connection lifetime, buffers, queues, and replay work.
- Enforce token expiry and lifecycle restrictions.
- Keep read state separate from socket delivery.
- Do not assume Kafka consumer groups broadcast to every instance.
- Preserve polling compatibility.

## Testing and verification

Use existing repository commands. Inspect build files, scripts, and CI before inventing new commands.

Test the behavior and risk introduced by the change:

- Unit tests for domain rules and transitions.
- Security tests for ownership, isolation, visibility, and lifecycle restrictions.
- Oracle integration tests for migrations and constraints.
- Kafka integration tests for retries, duplicates, and recovery.
- Object-store and identity-provider tests where relevant.
- Contract tests for changed interfaces.
- End-to-end tests for important cross-service workflows.

Do not substitute H2 and claim Oracle compatibility.

Use deterministic fixtures, injectable clocks, and condition-based asynchronous waits. Avoid arbitrary sleeps.

Never weaken assertions, disable authorization, or suppress failures merely to obtain a passing build.

When blocked, record the exact limitation and continue independent work. Clearly distinguish PASS, FAIL, BLOCKED, and
NOT RUN.

## Performance and operations

Measure before optimizing or adding infrastructure.

Report performance with:

- Hardware.
- Dataset.
- Replica count.
- Concurrency.
- Test duration.
- Throughput and latency.
- Errors and resource use.

Budget database connections across all replicas and workers.

Keep metric labels low-cardinality. Use logs and traces for individual identifiers.

Provide health probes, graceful shutdown, bounded timeouts, reproducible images, and migration procedures.

Run disruptive tests only in explicitly authorized disposable environments.

Do not claim production capacity, high availability, disaster-recovery guarantees, or regulatory compliance without
evidence.

## Documentation and checkpoints

Use existing documentation where possible.

For active release N, maintain:

- `docs/mvpN-plan.md`
- `docs/mvpN-progress.md`
- `docs/mvpN-verification.md`

Update relevant ADRs, API contracts, runbooks, and policies when behavior changes.

Keep documentation concise and consistent with implementation.

Before interruption, record:

- Current phase.
- Completed acceptance criteria.
- Remaining work.
- Failed or blocked checks.
- Relevant files and commands.
- Next executable action.

Resume from repository evidence rather than regenerating completed work.

## Completion report

Provide a concise final report containing:

- What changed and why.
- Verification performed and actual results.
- Remaining blockers or limitations.
- Relevant file links.
- Deployment status, if applicable.

Do not imply that planned features, unexecuted tests, or unverified deployments are complete.
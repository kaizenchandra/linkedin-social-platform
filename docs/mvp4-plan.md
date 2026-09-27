# MVP-4 implementation plan

Extend member, content, hiring and notification ownership; retain seven deployable applications and all existing APIs.
No framework upgrade, new dependency, search cluster or cache. Preserve the inspected MVP-3 baseline and unrelated
changes.

| Phase | Acceptance gate                                                                                       | Status |
|-------|-------------------------------------------------------------------------------------------------------|--------|
| 0     | MVP-3 baseline, privacy rules, feed plan, versioning and migrations                                   | PASS   |
| 1     | Idempotent member/company follows, concurrency constraints, block cleanup, no role grants             | PASS   |
| 2     | Expanded feed, private saved posts/jobs, current authorization and bounded advancing cursors          | PASS   |
| 3     | Deterministic explanations, exclusions, bounded queries and measured cost                             | PASS   |
| 4     | Prospective saved searches, immutable publications, durable matching and current delivery eligibility | PASS   |
| 5     | Oracle/Kafka rollback/recovery/replay, security/contracts, regressions, load and telemetry            | PASS   |
| 6     | Fresh/populated upgrade, Compose/kind journeys, persistence, CI and operational handover              | PASS   |

See [verification](mvp4-verification.md) for executed commands and [checkpoint](mvp4-progress.md) for the next
action. [Privacy and alert semantics](discovery-and-alert-semantics.md) defines the transaction and cancellation
boundaries; [ADR012](adr/012-discovery-and-job-alerts.md) records the worker decision.

Product bounds: 500 member follows and 500 company follows per actor; existing connections remain capped at 500. A feed
author set can contain 1,001 IDs and is never truncated. SQL author groups contain at most 500 IDs; candidate policy
checks contain at most 100 authors. Feed/bookmark requests scan at most 500 candidates. Saved searches are capped at 10
per member and 20 explicit companies per search.

Migrations are additive in the four affected owners. Runtime remains Java 21, the pinned Boot/Cloud BOM, Oracle and
Kafka. Local verification uses macOS ARM64 and Docker with 11 CPUs and approximately 9.4 GB RAM. Compose and kind run
serially because they share host ports and the memory budget. No shared or paid deployment is authorized.

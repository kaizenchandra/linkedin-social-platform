# MVP-3 progress

Current phase: **6 complete; MVP-3 /0.3.0 locally verified on2026-09-27**. No unresolved local blocker or incomplete required business feature. No later release started.

| Phase | Completed acceptance criteria |
|---|---|
| 0 PASS |Actual MVP-2 baseline:32 tests and both gateway journeys; company/privacy matrix, state machines, additive schema/snapshot/event design; no new dependency or framework upgrade |
| 1 PASS |Hiring-service, unverified companies, private logos, owner/recruiter authorization, invitation expiry/decisions, atomic ownership transfer and audit; real Oracle races and live isolation/revocation |
| 2 PASS |Validated jobs, idempotent publish/close, deadlines, literal Oracle search, structured indexes and deterministic cursor pagination; measured baseline |
| 3 PASS |Applicant-scoped idempotency and uniqueness, coherent versioned professional snapshot, transactional job snapshot, status/history, withdrawal redaction; closure/submission and competing-review races |
| 4 PASS |Transactional hiring events, current-recipient fan-out, atomic notification dedup, generic targets, audited moderator-only job reporting/hiding/restoration; real Kafka replay and failed lookup DLT/replay |
| 5 PASS |60 tests on fresh Oracle/S3 containers and dedicated Oracle; existing release regressions, security/contracts, broker/dependency/consumer failures, two relays, seven image scans, instrumented load and HTTP/Kafka traces |
| 6 PASS |Fresh Compose, populated MVP-2 schema upgrade, retained kind upgrade with unchanged old-data hashes, server-valid manifests, all deployed journeys, rolling restarts, six-schema and object backup/restore, CI/runbook/HTTP examples |

Final verification: `python3 scripts/verify-oracle-local.py` passed60 tests with zero failures/errors/skips in39.292s, including strengthened no-op/retry event-count and private-payload assertions. Restored Compose internal-scope checks, hiring contracts and telemetry passed afterward. Earlier fresh Testcontainers run passed60 tests in1m45s. Full commands, resolved failures and evidence are in [mvp3-verification.md](mvp3-verification.md).

Handover: original Compose data preserved and running; all seven applications, Oracle, Kafka, Keycloak and object storage healthy. Prometheus/Grafana/Zipkin running; seven authenticated metrics targets healthy. Dedicated `professional-network-mvp` kind cluster upgraded and verified, then stopped; PVCs retained. Keep it stopped while Compose uses8080/8180. Private test sessions restored for Compose; kind sessions saved under ignored `.local/` with mode0600.

NOT RUN: hosted CI; no external/shared/paid deployment or image publication. Limits: self-created companies are UNVERIFIED, retention is operator-managed with no automatic purge, substring search and company-level locking have documented small-workload limits, local infrastructure is single-node, and optional tracing storage evicts old spans. No production readiness, HA, employer verification or legal-compliance claim.

Next executable action: use the running APIs or `TEST_SESSION=.local/hiring-session.json python3 scripts/http-env.py` and [hiring.http](../requests/hiring.http). To repeat acceptance with isolated identities: `python3 scripts/auth-hiring-setup.py && python3 scripts/smoke-mvp3.py`. Begin another release only after a new request.

# MVP-4 completion checkpoint

Current phase: 6 — complete and locally verified on 2026-09-27. No required business logic remains as a placeholder. No
next release was started.

Implemented across member, content, hiring and notification services: private member/company follows, block cleanup,
expanded chronological feed, private saved posts/jobs, deterministic suggestions, prospective versioned saved searches,
durable matching, job-alert preferences and deduplicated generic notifications. Gateway and other service APIs remain
compatible. Added migrations, contracts, metrics/dashboard alerts, CI steps, deployment updates, runbook and IntelliJ
requests.

| Status  | Evidence                                                                                                                                              |
|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| PASS    | 77 Java tests; zero failures, errors or skips; final full suite 43.805 seconds                                                                        |
| PASS    | MVP-1 through MVP-4 real gateway journeys on Compose and kind                                                                                         |
| PASS    | Oracle constraints/rollback, Kafka outage/recovery/DLT replay, two healthy matching workers, durable checkpoint restart                               |
| PASS    | Current privacy, internal scopes, ownership, live API/event contracts and original notification JSON compatibility                                    |
| PASS    | Fresh Compose, populated MVP-3 upgrade, retained kind upgrade, 37 server-validated resources, rolling restart persistence and isolated backup/restore |
| PASS    | Seven pinned image scans with zero HIGH/CRITICAL findings; connected HTTP/Kafka traces; eight valid Prometheus rules                                  |
| PASS    | Measured load with dataset/hardware/concurrency/duration and actual tracing configuration; see verification reports                                   |
| FAIL    | No unresolved failures. The notification null-field compatibility defect and test setup issues are documented as resolved.                            |
| BLOCKED | None for local release acceptance                                                                                                                     |
| NOT RUN | Hosted CI; no external deployment was attempted                                                                                                       |

Current environment: all seven original Compose applications are healthy with optional observability. The dedicated
`professional-network-mvp` kind node is stopped to release ports 8080/8180; existing PVCs remain. These are single-node
local deployments, not production availability or capacity evidence.

Final commands included `verify-oracle-local.py`, `check-mvp4-recovery.py`, `check-mvp4-kind-release.py`,
`smoke-mvp4.py`, `check-mvp4-contracts-security.py`, and
`MVP4_LOAD_EVIDENCE=docs/mvp4-load-observed-release.json python3 scripts/load-mvp4.py`.
See [verification](mvp4-verification.md) for full commands, outcomes and evidence links. The generated historical MVP-3
benchmark change was restored; unrelated work was preserved.

Operational next action: use the running gateway and `requests/mvp4.http`. To repeat acceptance, run
`python3 scripts/auth-hiring-setup.py && python3 scripts/smoke-mvp4.py`; use the runbook for disruptive recovery or
cluster-switch checks. Resume only for a new request or a newly observed failure.

Remaining limits: hosted CI not executed; substring search and bounded discovery are not full-text/complete graph
search; alert latency grows with active search owners and backlog; current authorization has the documented
cross-service race boundary; delivered notifications are not recalled; retention/purge remains an explicit operational
policy.

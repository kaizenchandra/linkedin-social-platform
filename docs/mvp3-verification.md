# MVP-3 verification — 2026-09-27

| Gate | Result | Executed evidence |
|---|---|---|
| MVP-2 baseline | PASS |32 tests against Oracle/S3,37.926s; real `smoke.py` and `smoke-mvp2.py` with Keycloak identities |
| Companies, membership, logos | PASS |35-test phase1 suite; Oracle acceptance/cancellation and transfer/removal races; gateway roles, revocation, normalized slug and owned/private logo checks |
| Jobs and search | PASS |47 executions including10 repeated membership races; lifecycle/deadline/salary/optimistic-edit/literal-filter/cursor tests and gateway checks |
| Applications | PASS |57-test phase3 suite,38.050s; concurrent idempotent submissions, closure races, competing reviewers, coherent immutable profile/job snapshots, withdrawal redaction, company isolation and blocking boundary |
| Events and moderation | PASS |60-test phase4 suite,39.203s; live invitation/application/status Kafka notifications, audited moderator-only inspection/actions, hidden-job denial, restore preserves CLOSED |
| Fresh test infrastructure | PASS |`scripts/java21.sh -B -ntp clean verify`:60 tests, zero failures/errors/skips,1m45s; real Oracle and S3 Testcontainers |
| Kafka/dependency failures | PASS |`check-mvp3-recovery.py`: profile503 without incomplete application, broker outage commit/recovery, consumer restart, current recipients, same-ID DLT replay, two real relays |
| Regressions/security/contracts | PASS |MVP-1/MVP-2 journeys; independent service401, forged identities, internal scope403, ownership/company isolation; OpenAPI and real event/response contracts |
| Image vulnerabilities | PASS |All seven final0.3.0 images passed pinned Trivy HIGH/CRITICAL gate; zero findings at scan time |
| Load and observability | PASS |Instrumented workload below; HTTP/profile/outbox/Kafka/notification trace, seven authenticated metrics targets; private fixture absent from traces/logs |
| Populated MVP-2 upgrade | PASS |`check-mvp3-upgrade.py`: preserved profiles/private posts/interactions/media/messages/read positions/notifications; new hiring schema and notification fan-out |
| Fresh Compose | PASS |`check-mvp3-fresh-compose.py`: separate new volumes, all three release journeys, disposable project removed, original volumes retained |
| Local kind deployment | PASS |Retained MVP-2 cluster upgraded;37 resources server-validated, old business-data hashes unchanged, all journeys/contracts, real hiring event replay and rolling restarts |
| Persistence/backup | PASS |Compose and kind restarts preserve hiring/networking state and images; six-schema Data Pump isolated restore, application snapshots/statuses,32-object SHA-256 restore |
| Local configuration | PASS |Compose validation, Python compilation, shell syntax, structural manifests and whitespace checks |
| Hosted CI | NOT RUN |Workflow updated; local equivalents executed. No remote publish/shared/paid deployment |

No unresolved local blocker. Final `verify-oracle-local.py` rerun passed60 tests, zero failures/errors/skips in39.292s (/tmp/mvp3-final-tests.log), including explicit no-op/retry outbox counts and exclusion of private content from events. Restored Compose internal-scope, hiring contract and trace checks passed. All core services are healthy; optional monitoring is running; kind is stopped with data retained. See [handover checkpoint](mvp3-progress.md).

## Failures found and fixed

- A new phase1 test initially used a record field instead of its accessor; compilation corrected before the gate.
- Phase2 exposed a real ORA-00060 ownership-transfer/removal deadlock. Oracle trace `FREE_dia0_78_base_1.trc` showed a dependency-lock/row-lock cycle between company locking and membership deletion. The composite owner FK lacked a complete index. Additive hiring V5 indexes `(id,owner_id)`;10 repeated races then passed. IntelliJ had no runnable configuration for the new test module, so database runtime diagnostics supplied the evidence; user breakpoints were unchanged.
- The first phase3 HTTP attempt ran before Compose readiness and returned gateway500. After the readiness gate completed, the full flow passed.
- The recovery script initially recreated services without the optional tracing override. It now starts existing containers with a health wait, preserving configuration.
- Zipkin exhausted its default heap during the first load attempt. Local settings now bound memory to a256MiB heap and10000 retained spans in a512MiB container. The supported setting was checked against [Zipkin3.5.1 source](https://github.com/openzipkin/zipkin/blob/3.5.1/zipkin-server/src/main/resources/zipkin-server-shared.yml) and [official Docker documentation](https://github.com/openzipkin/zipkin/blob/master/docker/README.md). Trace checks passed before and after the repeated load. Local trace storage intentionally evicts old spans and is not durable.

No security checks or assertions were weakened to pass these gates.

## Measurements and artifacts

[Load result](mvp3-load-result.json): macOS ARM64, Docker11 CPUs/9,435,414,528 bytes, one replica/service, concurrency4. One isolated company with60 jobs and one applicant; older regression fixtures remain.60 submissions in0.946s:63.4/s,p95 204.3ms.20-second search:967.7/s,p95 7.4ms.20-second recruiter listing:832.7/s,p95 7.9ms. Zero errors. Resource snapshot, p50/p99 and limitations are in the artifact. Small local workload, not saturation testing or a production capacity claim. Company locking remains deliberately coarse; no measured need for a cache or search cluster.

- [Recovery](mvp3-recovery-evidence.json): broker-outage application committed in0.255s; same-key replay barrier, current membership and failed-recipient DLT replay verified.
- [Telemetry](mvp3-telemetry-evidence.json): one trace spans gateway, hiring, member and notification through Kafka; seven healthy Prometheus targets.
- [Populated upgrade](mvp3-upgrade-evidence.json), [fresh Compose](mvp3-fresh-compose-evidence.json), [retained kind data comparison](mvp3-kind-upgrade-evidence.json), [kind deployment](mvp3-kind-evidence.json).
- [Oracle restore](mvp3-backup-restore-evidence.json), [private object restore](mvp3-object-backup-evidence.json).

Reusable commands are in [the runbook](runbook.md#mvp-3-upgrade-and-hiring-operations). Local transient logs: `/tmp/mvp3-baseline.log`, `/tmp/mvp3-phase{1,2,3,4}-*.log`, `/tmp/mvp3-fresh-tests.log`, `/tmp/mvp3-recovery.log`, `/tmp/mvp3-load-fixed.log`, `/tmp/mvp3-telemetry-fixed.log`, `/tmp/mvp3-upgrade.log`, `/tmp/mvp3-backup.log`, `/tmp/mvp3-fresh-compose.log`, `/tmp/mvp3-kind-release.log`, `/tmp/mvp3-final-scans.log`. Durable facts are recorded here and in JSON artifacts; transient logs are not part of a clean checkout.

Companies remain UNVERIFIED. Retention is [operator-managed policy](hiring-retention-policy.json), not automatic erasure or legal compliance. Oracle/Kafka/object storage/Keycloak are single-node local infrastructure. Optional monitoring is verified in Compose, not deployed into kind. Hosted CI and production operations remain outside this local evidence.

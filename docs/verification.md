# Verification evidence

Verified locally on 2026-09-27. Host: macOS ARM64, JDK21.0.12.1; Docker29.4.0, Compose5.1.2, 11 Docker CPUs / 9 GB
memory. Build: Maven Wrapper3.3.4 / Maven3.9.11, Boot4.0.8, Cloud2025.1.3, Tomcat11.0.26. Dedicated kind0.33.0 /
Kubernetes1.37.0. Shell defaults differed, so `scripts/java21.sh` explicitly selects Java21.

## Phase gates

| Phase                          | Status | Executed evidence                                                                                                                                                                                                                |
|--------------------------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0: discovery and compatibility | PASS   | Official BOM/artifact/image verification; Java21 builds; Oracle23.9 ARM64 runs. Sources in technology-decisions.md. Existing prompts and IDE files preserved.                                                                    |
| 1: foundation and identity     | PASS   | All modules packaged; Oracle migrations; healthy Compose infrastructure; gateway real-token profile request and anonymous401; S256 code exchange, refresh and logout.                                                            |
| 2: profiles and connections    | PASS   | Real Oracle reciprocal-request race creates one canonical relationship; concurrent acceptance is deterministic; recipient/ownership restrictions; populated schema migration.                                                    |
| 3: content and feed            | PASS   | Concurrent likes, ownership, cascade deletion, Unicode text, cursor pagination; connected feed and immediate removal; member outage produces503 with no partial feed.                                                            |
| 4: events and notifications    | PASS   | Atomic Oracle dedup, self-action suppression, mark-read idempotency; real replay, broker outage and recovery, consumer restart; concurrent relays; rejected event sent to DLT and repaired with same eventId.                    |
| 5: operational verification    | PASS   | 20 automated tests without failures/skips; real security/contract checks; HTTP and async traces; four authenticated Prometheus targets; load results; four application images with zero HIGH/CRITICAL findings.                  |
| 6: deployment and recovery     | PASS   | Compose and kind smoke; four application rolling restarts preserve business state; Oracle Data Pump restore to isolated schemas; Kubernetes server validation; seven healthy deployments, four completed jobs, three bound PVCs. |

No unresolved FAIL or BLOCKED local gates. NOT RUN: remote GitHub Actions execution, telemetry deployment in kind,
production deployment/capacity/HA testing, Keycloak disaster recovery. The optional telemetry stack was verified in
Compose. Final Compose concurrent-relay script adds a readiness wait; that exact added wait was not rerun after Compose
stopped. Kind independently passed the stronger test with two confirmed ready replicas and twenty actions.

## Automated and live commands

Clean-checkout Oracle Testcontainers path passed all 20 tests (14 unit/security/domain and six Oracle integration
tests), with zero failures/errors/skips:

```sh
# OrbStack on this host; omit these variables when Docker discovery works normally.
DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
scripts/java21.sh -B -ntp clean verify
```

The final Java changes also passed all 20 tests through `python3 scripts/verify-oracle-local.py` against the dedicated
Compose Oracle schemas. [Test inventory](test-evidence.json) records the individual suites. No H2 substitution was used.
Signed-JWT tests exercise invalid signature, issuer, audience, subject and expiry. Real HTTP checks additionally cover
direct service authentication, owner isolation, forged identity fields and oversized chunked bodies.

Executed against Compose:

```sh
python3 scripts/auth-test-setup.py
python3 scripts/check-pkce.py
python3 scripts/smoke.py
python3 scripts/check-security.py
python3 scripts/check-event-recovery.py
python3 scripts/check-dead-letter.py
python3 scripts/refresh-session.py
python3 scripts/check-restart.py compose
python3 scripts/check-backup-restore.py
.local/venv/bin/python scripts/check-contracts.py
python3 scripts/check-telemetry.py
python3 scripts/load.py --seconds 20 --concurrency 4
scripts/scan-images.sh
```

Contract tooling was installed from `scripts/requirements.lock`. The critical user journey provisions three real
identities, establishes a connection, posts/feeds/likes/comments, checks author notifications and stranger isolation,
then removes the connection. Replay and restart scripts extend the same stored test session. Kafka recovery tests use
the secured broker and scoped application principals.

Executed against the dedicated kind cluster after `scripts/deploy-kind.sh`:

```sh
python3 scripts/auth-test-setup.py
python3 scripts/check-pkce.py
python3 scripts/smoke.py
.local/venv/bin/python scripts/check-contracts.py
python3 scripts/check-kind-events.py
python3 scripts/refresh-session.py
python3 scripts/check-restart.py kind
scripts/kubectl-local.sh apply --dry-run=server -f infra/k8s/infrastructure.json
scripts/kubectl-local.sh apply --dry-run=server -f infra/k8s/migrations.json
scripts/kubectl-local.sh apply --dry-run=server -f infra/k8s/applications.json
```

All succeeded. The final `deploy-kind.sh` was rerun successfully after the pinned-image alias fix; resources remained
healthy and unchanged. Python/shell syntax, JSON parsing, manifest structure and final working-file whitespace checks
passed. [Deployment evidence](kind-deployment-evidence.json) records the final health snapshot. The two-replica test
returns content-service to one replica. No default/shared kubeconfig or cluster was used; no images were externally
published.

## Measurements and recovery evidence

- [Load](load-result.json): 20.005 seconds, concurrency4, one replica per app, 9,280 own-profile reads, 463.89
  requests/second, zero errors, p95 16.51 ms and p99 25.92 ms. Dataset, host, tracing configuration and resource
  snapshot are recorded. This is a short read workload, not a saturation or capacity test.
- [Telemetry](telemetry-evidence.json): one gateway/business HTTP trace and one gateway/content/Kafka/notification
  trace; four authenticated Prometheus targets. JSON application logging and correlation/trace integration are
  configured; a separate assertion of every emitted log field was not recorded.
- [Security scan](security-scan-summary.json): Trivy0.74.0 found zero HIGH/CRITICAL issues in all four final application
  images, including their Java dependencies. Scope excludes infrastructure images and other severities.
- [Backup/restore](backup-restore-evidence.json): real Data Pump flashback-SCN export, imports into three fresh
  disposable schemas, ten table counts and profile/post text compared; original data preserved, temporary schemas
  removed. Application state also survived Compose and kind application restarts.

## Failures found and corrected

Real checks caught and resolved a missing Keycloak subject mapper, Boot4 RestClient starter omission, Oracle timestamp
precision on repeated mark-read, Spring Kafka4's different default DLT suffix, an incompatible producer-listener generic
type, and Tomcat vulnerabilities in the managed patch version. Data Pump import now excludes pre-created USER metadata
while retaining table/data comparisons.

Kind exposed incomplete local multi-platform image indexes and Oracle faststart files missing on an empty PVC.
Deployment now exports the host platform and seeds only a fresh Oracle data directory. Kafka's headless bootstrap
service publishes not-ready addresses; application liveness and readiness probes use distinct endpoints. These fixes
passed deployment and acceptance checks. Secrets remain outside tracked files.

## Compose port-conflict recovery — PASS (2026-09-27)

Confirmed the dedicated kind node owned host ports8080/8180. Stopped that node without deleting it, resumed
`docker compose up -d --wait --wait-timeout 240`, and recreated only Keycloak after Docker retained a missing host-port
binding from its failed start. All seven long-running Compose services are healthy; host OIDC discovery returns200 with
the expected issuer, and the gateway protected profile endpoint returns401 without authentication. Kind is stopped and
its data retained. README/runbook now explain switching and binding recovery; `git diff --check` passed. No application
code changed, so the full test suite was not rerun for this operational fix.

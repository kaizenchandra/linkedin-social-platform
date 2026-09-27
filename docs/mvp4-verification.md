# MVP-4 verification

Executed locally on 2026-09-27 using Java 21, real Oracle 23.9, Kafka, Keycloak and private S3-compatible storage. Tests use disposable schemas; H2 was not substituted. Existing uncommitted MVP-3 work was preserved.

| Phase | Executed gate | Result |
|---|---|---|
| 0 | MVP-3 baseline: `verify-oracle-local.py` and `smoke-mvp3.py` | PASS: 60 tests, no failures/errors/skips; 49.773 seconds; real gateway journey passed |
| 1 | Follows, concurrent duplicates, follow/block race, connection independence, private company listing | PASS: 64 tests; 42.317 seconds; rebuilt Compose journey passed |
| 2 | Expanded feed and bookmarks, including 501 inaccessible saved candidates and advancing cursor | PASS: 67 tests; 46.807 seconds; Compose journey passed |
| 3 | Oracle suggestion ranking/exclusions and explicit company filtering | PASS: 69 tests; 43.649 seconds; gateway and query measurement passed |
| 4 | Prospective searches, immutable publication, matching, preferences and real Kafka replay | PASS: 75 tests; 45.566 seconds; overlapping searches produced one alert |
| 5 | Final security, rollback, concurrency, contracts and regressions | PASS: 77 tests, no failures/errors/skips; 43.805 seconds in the final run |
| 6 | Fresh Compose, populated upgrade, persistence and backup/restore | PASS: Compose and kind journeys, upgrades and rolling restarts verified |

Final test command: `python3 scripts/verify-oracle-local.py`. Logs: `/tmp/mvp4-compatibility-tests.log`. The verifier creates six disposable Oracle schemas and drops only those fixtures on exit, preventing live background workers from interfering with test data.

## Reliability and authorization

`python3 scripts/check-mvp4-recovery.py` passed against real Compose infrastructure:

- Publication committed in 0.067 seconds while Kafka was stopped, and its alert arrived after recovery.
- Hiring eligibility outage caused bounded notification retries and DLT without committing deduplication. Repaired same-ID replay remained unique.
- A job-scoped Oracle trigger injected a real outbox insert failure. Match, outbox and checkpoint rolled back; five attempts reached FAILED; operator replay succeeded.
- Restart preserved a partially completed owner cursor and completed 81 unique matches, outbox records and notifications.
- A separate publication was created only after two worker instances were healthy. It produced exactly 81 matches, 81 outbox records and 81 notifications. The temporary second instance was removed.

Evidence: [recovery report](mvp4-recovery-evidence.json), `/tmp/mvp4-recovery-final.log`. The strengthened two-ready-worker fixture replaces an earlier ambiguous test in which the first worker could finish while the second started.

`check-mvp4-member-outage.py` passed: stopping member-service made feed and saved-post listing return retriable 503, then recover. Live OpenAPI/event/security checks passed, including anonymous rejection, internal service scopes, moderator-only replay, forged ownership, bounded inputs and safe generic alert text. Oracle rollback assertions verify committed-match metrics do not increment on rollback.

MVP-1, MVP-2 and MVP-3 gateway journeys and existing contract checks passed (`/tmp/mvp4-regressions.log`). The unchanged MVP-3 journey passed again after the notification compatibility fix (`/tmp/mvp4-compatibility-regression.log`).

## Measurements and observability

A 20-second mixed local read run used ARM64, 11 Docker CPUs, 9.44 GB Docker memory, one replica per service and concurrency 4. It executed 9,785 requests at 489.0 requests/second, p95 19.4 ms, p99 28.1 ms, with zero errors. Five publication-to-alert samples were 1.74–2.23 seconds. Dataset and resource details are in [load evidence](mvp4-load-evidence.json).

Before the scheduling correction, matching took 20.0–20.5 seconds because a single pending publication used only one of 25 available transactions per tick. The worker now uses the bounded budget across pending work. [Earlier measurements](mvp4-load-before-worker-budget.json) are retained. Read-rate differences are not attributed to this change; these are warm local measurements, not saturation or production-capacity claims. The trace agent was disabled during load measurement.

The discovery baseline ran 100 requests at concurrency 2 in 0.559 seconds, 178.8 requests/second, p95 15.5 ms and zero errors on a small local dataset. See [discovery evidence](mvp4-discovery-baseline.json). No cache or search infrastructure was added.

Optional observability verification passed with connected gateway/hiring/notification HTTP, Kafka and durable-workflow trace context, current Prometheus metrics, and no private fixture text in logs/traces. See [telemetry evidence](mvp4-telemetry-evidence.json). `promtool check rules /etc/prometheus/alerts.yml` passed all eight rules.

A final traced run is recorded separately in [observed release load](mvp4-load-observed-release.json): 3,149 retained posts, 356 profiles and 187 active search owners; one replica per service, concurrency 4, 20.004 seconds, 6,358 requests, 317.8 requests/second and zero errors. Feed p95 was 56.2 ms; member/company suggestion p95 was 14.6 ms. Five alert samples were 5.23–8.92 seconds. Tracing was enabled in all participating services. This larger retained dataset and instrumentation differ from the earlier run; the reports are not an isolated tracing-cost comparison. Matching work grows with eligible search owners while each transaction and tick remain bounded; no alert-latency SLA is claimed.

Final [environment evidence](mvp4-environment-evidence.json) confirms all seven Compose applications healthy and the dedicated kind node stopped with PVCs retained. Feed counters since content-service startup recorded 26,544 scanned and 26,543 returned candidates, including the final smoke and traced load. The final live OpenAPI/event/security suite and generic alert-text assertion also passed after Compose restoration.

## Deployment and recovery

- All seven final 0.4.0 images passed pinned Trivy 0.74.0 scanning with zero HIGH/CRITICAL findings. [Scan evidence](mvp4-scan-evidence.json) records image IDs and scan times.
- `check-mvp4-upgrade.py` initialized isolated schemas at member 6, content 7, notification 4, media 2, messaging 2 and hiring 7; populated prior-release data; then applied additive migrations. Profiles, posts, messages/read positions, companies, jobs, applications, snapshots and history were preserved. New publication/match tables remained empty. [Upgrade evidence](mvp4-upgrade-evidence.json).
- `check-mvp4-restart.py` passed exact discovery-state comparison across Compose application restarts.
- `check-mvp4-backup.py` passed a Data Pump flashback export and restore into six disposable schemas, comparing all table counts and saved-search/matching checkpoints. [Backup evidence](mvp4-backup-restore-evidence.json).
- `check-mvp4-fresh-compose.py` started a temporary project from empty volumes, passed all four release journeys, removed only its own volumes and restored the original stack/sessions. [Fresh Compose evidence](mvp4-fresh-compose-evidence.json). The generic notification display field was added after that image capture; final tests and gateway checks verify its compatible serialization.
- All 37 Kubernetes resources passed structural and server-side validation. The retained dedicated kind cluster upgraded from 0.3 to 0.4 with unchanged business-data hashes and existing PVCs. [Kind upgrade evidence](mvp4-kind-upgrade-evidence.json). All MVP-1/2/3/4 gateway journeys and rolling restarts passed after the compatibility fix; [kind release evidence](mvp4-kind-release-evidence.json). The dedicated node was then stopped, PVCs retained, and original Compose with observability restored. Log: `/tmp/mvp4-kind-final.log`.

## Resolved failures and remaining execution limits

The first kind journey detected that adding `message:null` changed existing notification JSON keys. The fix omits only the null field; the original assertion was preserved and a serialization regression assertion added. Final Java tests and the original MVP-3 journeys now pass on both Compose and kind. No assertion was weakened.

Other resolved setup failures: no Spotless plugin exists (the established formatter was used); an expired test token was refreshed through the supported flow; telemetry first ran without the optional agent and later before discovery counters had been exercised. The telemetry script now exercises counters and waits conditionally for scraping.

Hosted CI: NOT RUN. Local equivalent build, integration, contract, scan and deployment checks are recorded above. These are dedicated single-node development environments, not high availability or production-capacity evidence. No shared cluster or paid infrastructure was used.

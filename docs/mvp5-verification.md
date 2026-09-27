# MVP-5 executed verification

Date:2026-09-27. Java21, Boot4.0.8, Cloud2025.1.3 and existing Oracle/Kafka stack retained. Application artifacts0.5.0.
Required local release gates PASS; no unresolved FAIL/BLOCKED. Hosted CI and manual browser UI run NOT RUN. The
development client's JavaScript behavior and real HTTP SSE clients were executed.

| Phase               | Executed commands/evidence                                                            | Result                                                                                                                                                                                             |
|---------------------|---------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0 baseline          | `verify-oracle-local.py`, `auth-hiring-setup.py`, `smoke-mvp4.py`                     | PASS77tests,0failures/errors/skips; real gateway baseline                                                                                                                                          |
| 1 durable history   | Full Oracle suite; owner-lock contention, rollback, cursor/floor tests                | PASS80tests; late commits cannot fall behind acknowledged owner positions                                                                                                                          |
| 2 messaging         | `check-mvp5-messaging.py`, `check-mvp5-expiry.py`                                     | PASS actual gateway flush, two devices, replay, foreign cursor, no bodies; actual5s Keycloak token closes both streams and reconnect401                                                            |
| 3/4 reads/controls  | `check-mvp5-controls.py`, Oracle read/preference/consumer tests                       | PASS private controls, monotonic concurrent reads, notification sync, positive Kafka mute barriers, unarchive, block/history and scoped403                                                         |
| 5 reliability       | `check-mvp5-failures.py`, `check-mvp5-mute-recovery.py`, `check-mvp5-limits.py`       | PASS two instances, gateway/service restart, Oracle pause/readiness503, broker outage, authority retries/DLT/repaired replay, slow TCP reader, retention410/reset,429admission and concurrent REST |
| 5 full build        | Final `verify-oracle-local.py`                                                        | PASS84tests,0failures/errors/skips,48.039s; all seven images rebuilt                                                                                                                               |
| 5 contracts/client  | `check-mvp5-contracts.py`, `check-contracts.py`, `check-realtime-client.cjs`          | PASS live schemas/CORS/auth, duplicate/stale read handling, account issuer+subject isolation, reset/count reconciliation; no URL/persisted browser tokens                                          |
| 5 identity/security | `check-pkce.py`, `check-security.py`                                                  | PASS real S256 code flow/refresh/logout; independent service401, forged identity400, chunked size413                                                                                               |
| 5 observation       | `load-mvp5.py`, `check-mvp5-telemetry.py`, Prometheus `promtool check rules`          | PASS measured load, exact committed-outbox trace linkage, private-body canary absent from inspected logs/trace, low-cardinality metrics,11valid rules                                              |
| 6 upgrade           | `check-mvp5-upgrade.py`                                                               | PASS populated MVP4 targets7/8/5/2/2/11 to latest; prior rows preserved, two default preferences backfilled, old reads retained/version0, no synthetic replay backfill                             |
| 6 fresh deployment  | `check-mvp5-fresh-compose.py`                                                         | PASS empty volumes/migrations, MVP1–5 gateway journeys; only temporary project removed, original volumes restored                                                                                  |
| 6 kind              | `check-mvp5-kind-release.py`, resumed at `--resume-restart` after fixture repair      | PASS retained0.4->0.5 data hashes,37server-validated resources, two messaging/two notification pods, replay through gateway after pod termination, all old journeys and seven rolling restarts     |
| 6 persistence       | `check-mvp5-restart.py` on Compose and kind; `check-mvp5-backup.py` with apps stopped | PASS exact messages/read/preferences/counts/cursors; consistent Data Pump remap into isolated schemas compares51tables and replay/read/preference values; originals preserved                      |
| 6 scanning          | `scripts/scan-images.sh`                                                              | PASS seven0.5.0 images, Trivy0.74.0 reports0HIGH/CRITICAL OS/bundled dependency findings                                                                                                           |

Final `smoke-mvp5.py` PASS after restoring Compose. Final health check: all seven applications and core infrastructure
healthy; monitoring running. Verified kind node stopped with PVCs retained. `git diff --check`, Python syntax,
JavaScript syntax, JSON and manifest structure checks PASS.

## Evidence artifacts

- [Failure/recovery](mvp5-failure-evidence.json), [mute authority/DLT](mvp5-mute-recovery-evidence.json), [slow reader/retention/admission](mvp5-limits-evidence.json).
- [Measured load](mvp5-load-evidence.json), [trace/metrics](mvp5-telemetry-evidence.json), [image scans](mvp5-scan-evidence.json).
- [Populated upgrade](mvp5-upgrade-evidence.json), [fresh Compose](mvp5-fresh-compose-evidence.json), [retained kind upgrade](mvp5-kind-upgrade-evidence.json), [kind release](mvp5-kind-release-evidence.json), [two-pod recovery](mvp5-kind-rolling-evidence.json).
- [Compose restart](mvp5-compose-restart-evidence.json), [kind restart](mvp5-kind-restart-evidence.json), [backup/restore](mvp5-backup-restore-evidence.json).

Raw final build `/tmp/mvp5-final-suite.log`; fresh `/tmp/mvp5-fresh.log`; kind `/tmp/mvp5-kind-resume.log`; backup
`/tmp/mvp5-backup.log`. These temporary logs are local execution evidence, not repository dependencies. Maven XML
reports remain in module target directories; scanner reports in.local/scans.

## Measurements and limits

Apple M3 Pro/18GiB host, local Docker11CPUs/approximately9GB. Load: two messaging, two notification and two gateway
instances;12streams/3accounts/1conversation/20messages over20.273s at0.987messages/s,0errors. RESTp95=79.7ms; event
creation-to-client observationp95=990.9ms; HTTP acknowledgement-to-observationp95=985.0ms. These bracket
commit-to-observation on the local clock; occurredAt is assigned before commit. End-of-run stream-service
memory410–484MiB under640MiB caps. Instrumentation configuration was not captured in this first load artifact; the
reusable script now records it on future runs. This small warm fixture is not saturation/capacity evidence.

Slow-reader observation15.48s includes filling buffers and Prometheus scrape delay, not a claimed15s configured
deadline. The configured client write deadline is5s. A direct Docker-network Java socket was necessary because the
host-port proxy buffered the entire replay. Socket writes are not acknowledged user delivery/read state.
Oldest-active-buffer age is measured; no global offline-client acknowledgement/backlog is invented.

## Failures found and resolved

- Initial gateway customizer used an unavailable API; compilation failed. Replaced with native response integration in
  the authenticated route filter; focused/full builds and real transport tests pass.
- Host-side stopped-reader tests failed to stimulate backpressure through Docker Desktop. Added SSE-only write
  deadline/bounds and a direct-network probe; it now requires an actual timeout metric plus replay recovery. No
  assertion weakened.
- A misplaced Compose grace-period insertion and temporary class cleanup ownership error were corrected; configuration
  and full fixture reruns pass.
- Kind's internal-scope test initially used Compose-only port8085. A temporary loopback Kubernetes port-forward
  preserves the403 assertion.
- Exact kind restart fixture originally awaited only one of two independently partitioned setup notifications. It timed
  out on equality. Waiting positively for both effects before snapshot fixes the race; the unchanged full state-equality
  and subsequent-stream assertions pass.

No framework downgrade, disabled security, skipped failing test or production/exactly-once/immediate-revocation claim.
Optional observability remains separate from basic startup. Scanner results are limited to its executed
database/severity scope.
See [protocol](realtime-protocol.md), [technology evidence](technology-decisions.md#mvp-5-2026-09-27)
and [runbook](runbook.md#mvp-5-upgrade-and-operations).

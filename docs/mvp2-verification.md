# MVP-2 verification

Release **0.2.0**, executed locally on **2026-09-27**. Java 21, macOS ARM64, Docker/OrbStack with 11 CPUs and approximately 9 GB RAM. No H2 substitution for service databases. Compose and kind run serially and keep separate datasets.

| Gate | Result | Executed evidence |
|---|---|---|
| Phase 0: MVP-1 baseline | PASS | `verify-oracle-local.py`: 20 existing tests, no failures/skips; real Keycloak PKCE and `smoke.py` |
| Phase 1: blocking/visibility | PASS | 22 tests; populated V5 migrations; `check-mvp2-privacy.py`; original smoke retained |
| Phase 2: images/lifecycle | PASS | 27 tests; `check-mvp2-media.py`, `check-media-recovery.py`; malformed/oversized bytes, foreign attachment, restricted download, interrupted commit and fenced cleanup |
| Phase 3: messaging | PASS | 29 tests; `check-mvp2-messaging.py`; actual concurrent conversation/send commands, deduplication, monotonic reads, participant isolation and disconnected history |
| Phase 4: moderation | PASS | 32 tests; `check-mvp2-moderation.py`; trusted role, private reports, audited inspection/actions, hidden comments/counts/media, original visibility after restore and author-deletion protection |
| Final clean Maven build | PASS | `scripts/java21.sh -B -ntp clean verify`: 32 tests, zero failures/errors/skips, fresh Oracle and S3 Testcontainers, 78 seconds; `/tmp/mvp2-clean-testcontainers.log` |
| Broker/consumer/dependency failures | PASS | `check-mvp2-failures.py`: authoritative policy outage fails closed with 503; unavailable object storage rejects upload; message commits during Kafka outage in 0.319 seconds and later delivers; consumer restart catches up |
| Replay and DLT | PASS | `check-mvp2-replay.py` on Compose and kind; `check-dead-letter.py` exercises rejected schema, bounded retries, dead-letter publication and repaired replay without duplicate effects |
| Database/application restart | PASS | `check-mvp2-restart.py database`: exact profile/avatar, post/image bytes, messages/read positions and unread counts preserved |
| Populated MVP-1 upgrade | PASS | `check-mvp2-upgrade.py`: isolated member/content V4 and notification V3 schemas populated before upgrade; text/interactions/notifications/dedup preserved, posts default MEMBERS; fixtures removed |
| Storage permissions | PASS | `check-s3-permissions.py`: own-bucket list allowed; application bucket creation and unrelated-bucket read denied; anonymous storage access denied |
| Telemetry | PASS | `check-telemetry.py`: six authenticated Prometheus targets; gateway/content/notification async trace, gateway/messaging/notification trace and media/owner authorization trace; Prometheus validates four alert rules |
| Local load | PASS | `load-mvp2.py --seconds 20 --concurrency 4`: 2,716 requests in 20.018 seconds, 135.68 requests/second, p95 63.20 ms, zero errors |
| Application/dependency scan | PASS | `scan-images.sh`: Trivy 0.74.0, zero HIGH/CRITICAL findings across six final application images and bundled dependencies |
| Quiescent backup/restore | PASS | `check-mvp2-backup.py`: five schemas, all table counts, profile/post/message text and read positions; `check-object-backup.py`: 16 private objects restored into separate bucket with SHA-256 equality; originals preserved |
| Fresh kind installation | PASS | `deploy-kind.sh`: ten ready Deployments, four bound PVCs, five migration Jobs and ACL Job complete; all 34 resources accepted by server dry run |
| Kind API and concurrency | PASS | Real PKCE, MVP-1 smoke, `smoke-mvp2.py`, contracts and replay; `check-kind-events.py`: two ready content relays deliver 20 events, two ready messaging relays deliver 10 events without duplicate notifications |
| Kind rolling restarts | PASS | `check-mvp2-restart.py kind`: all six application rollouts and exact persisted state/image equality |
| Fresh Compose installation | PASS | Separate project with new volumes: ten healthy services, six successful jobs, PKCE, both release journeys, six-service JWT/forged-identity checks, S3 permissions, replay and contracts; original data preserved |
| Remote GitHub Actions | NOT RUN | Workflow updated; local checks provide the evidence above |

All Python commands above are in `scripts/`. Contract validation uses `.local/venv/bin/python scripts/check-contracts.py` after installing `scripts/requirements.lock`. On OrbStack, the clean Maven command used `DOCKER_HOST=unix:///Users/mwgbh/.orbstack/run/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

## Findings fixed during verification

- SeaweedFS rejected streaming-chunk checksums. The bounded S3 client now uses fixed-size signed uploads with explicit SHA-256 and enabled download checksum validation; live uploads and tests pass.
- Hibernate 7 expected Oracle native BOOLEAN. Explicit numeric boolean conversion matches the additive constrained NUMBER(1) migration; schema validation remains enabled.
- Applications could exit while Oracle restarted. Long-running Compose services now have `unless-stopped`; migration/ACL jobs remain one-shot. Database restart recovery passes.
- Fresh kind pod readiness preceded host NodePort availability. Provisioning now waits up to 120 seconds for OIDC discovery before admin mutations. Stopped-node detection uses the dedicated Docker node directly and waits for its API.
- Earlier documentation said an old kind node was retained, but none existed when deployment ran. The executed kind test is a **fresh installation**, not a populated kind upgrade. Populated upgrades were verified separately against Oracle and the existing Compose installation.

## Evidence files and limits

Evidence: [upgrade](mvp2-upgrade-evidence.json), [recovery](mvp2-recovery-evidence.json), [load](mvp2-load-result.json), [traces](mvp2-telemetry-evidence.json), [scan](mvp2-security-scan-summary.json), [database restore](mvp2-backup-restore-evidence.json), [object restore](mvp2-object-backup-evidence.json), [kind](mvp2-kind-evidence.json), [fresh Compose](mvp2-fresh-compose-evidence.json).

Load evidence is a short read-only run with tracing enabled and a small three-member/moderator fixture: one private post/image and one conversation. It is not a saturation test or production capacity estimate. Scans cover application images and HIGH/CRITICAL severities, not every infrastructure image or every severity. Optional observability was demonstrated in Compose, not deployed in kind; alert rules have no external receiver.

Final handover PASS: original Compose restored with ten healthy core services, optional observability running, six authenticated metrics targets up, original test session refreshed and live contracts rechecked. The verified kind node is stopped with data retained; the disposable fresh-Compose project was removed. Python/shell syntax, JSON/YAML parsing and Git whitespace checks pass.

No unresolved test failures at the last completed gate. Cross-service policy checks retain a documented check-to-commit concurrency window; already-authorized streams may finish after policy changes. Upload concurrency limits are instance-local; hourly upload quotas may overshoot across replicas. More than 1,000 distinct interaction actors per type fails closed with 503. Retained messages/tombstones/audits/backups do not establish complete erasure. Local single-node infrastructure, backup checks and this release do not establish production HA, identity-provider disaster recovery or regulatory compliance. See the [privacy matrix](privacy-policy-matrix.md) and [runbook](runbook.md).

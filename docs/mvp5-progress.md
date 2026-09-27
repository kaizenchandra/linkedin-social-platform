# MVP-5 checkpoint

Current phase: **6 complete, locally verified on2026-09-27**. No subsequent release started. Existing work preserved; no sub-agents used.

Implemented:
- Separate authenticated owner SSE streams for messaging and notifications, with Oracle history written in the business transaction, commit-ordered cursors, conservative synchronization boundary and24-hour configurable replay retention/reset.
- Multi-device monotonic read synchronization and authoritative counts; private mute/archive, atomic message-triggered unarchive, current scoped mute checks before notification creation.
- Bounded servlet polling/writes, gateway admission/write deadlines, token-expiry closure, two-instance recovery and a memory-only fetch verification client. REST/polling remain available.
- Additive migrations,0.5.0 images, two stream replicas in kind, contracts, CI, dashboards/alerts, upgrade/backup/runbook and IntelliJ examples.

Changed modules: platform-web technical stream utilities; messaging-service V3/V4 and APIs; notification-service V6/V7 and consumer/APIs; gateway stream transport/security routing. Other service implementations retain their ownership; artifact versions/build/deployment references advance to0.5.0. Keycloak adds a narrow notification-to-messaging scope. No framework upgrade or new service.

Verification PASS:
- Full Maven suite84tests,0failures/errors/skips; real Oracle integration;48.039s. MVP4 baseline77tests was passed before changes.
- Real gateway messaging/notification streams, two-device reads, replay/foreign/reset/expiry, mute/archive/block history, Kafka dedup/DLT and authority failure recovery.
- Two instances, gateway/service restart, Oracle interruption/readiness503, Kafka outage, actual slow-reader disconnect/replay, finite admission and measured12-stream load.
- Fresh Compose and MVP1–5 journeys; populated MVP4 schema upgrade; retained kind0.4->0.5 hashes,37server-validated resources, two ready pods per stream service, termination/reconnect and seven rolling restarts.
- Exact persisted read/preference/cursor state across Compose and kind restarts; Data Pump isolated restore compares51tables and new replay/read/preference fields.
- Real PKCE/refresh/logout, independent service authentication, forged identity/size checks, live contracts, Node client behavior, exact-outbox OTel trace/privacy check,11Prometheus rules, seven-image scan0HIGH/CRITICAL.

Current environment: original Compose applications/infrastructure and optional monitoring running; all application health checks healthy. Dedicated kind node stopped with PVCs retained to release8080/8180. All temporary peer containers, fresh-test volumes and restore schemas removed. Required local gates: no unresolved FAIL or BLOCKED. Hosted CI and manual browser UI exercise: NOT RUN; automated JavaScript behavior and actual HTTP SSE clients passed.

Next executable action for a reviewer: `python3 scripts/smoke-mvp5.py`, or serve `tools/realtime` onlocalhost3000 and provide a fresh PKCE token. Use [verification](mvp5-verification.md), [protocol](realtime-protocol.md), [runbook](runbook.md#mvp-5-upgrade-and-operations) and requests/mvp5.http. No further implementation work is pending within MVP5.

Limits: at-least-once browser events, finite history, instance-local admission, JWT expiry rather than immediate revocation, conservative snapshots rather than frozen pagination. Local measurements are not production capacity/HA evidence. See protocol for mute cancellation boundary and metrics that distinguish persistence, emission, observation and explicit reading.

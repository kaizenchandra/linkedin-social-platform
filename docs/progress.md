# Project progress

Current release: **MVP-2 / 0.2.0**, phases 0–6 locally verified on 2026-09-27. See [MVP-2 progress](mvp2-progress.md) and [executed verification](mvp2-verification.md).

Six applications implement profiles/connections/content/notifications plus private images, visibility/blocking, one-to-one messages and audited moderation. Fresh Compose and dedicated kind journeys, populated upgrade, real Oracle/S3 tests, Kafka recovery, rolling restarts and disposable backup/restore pass. Remote CI is NOT RUN; no external deployment or publishing occurred.

The original Compose dataset is running with ten healthy core services and optional observability; the dedicated kind node is stopped. Keep it stopped while Compose runs: both bind8080/8180. The earlier Keycloak port-allocation failure was resolved by stopping the competing local kind node and recreating Keycloak with its named volume retained. The switching and missing-port recovery commands remain in the [runbook](runbook.md#switching-between-compose-and-kind).

Historical MVP-1: four applications and20 tests passed all local gates before this extension; its evidence remains in [verification.md](verification.md). The prior record of a retained kind node was stale when this release deployed, so MVP-2 kind evidence is explicitly a fresh installation. Populated Oracle upgrades were verified separately.

No unresolved local release blocker. Use the running APIs or IntelliJ requests; do not start another release without a new request.

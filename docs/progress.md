# Current release

MVP-5 / 0.5.0: phases0–6 locally verified, including two-pod replay, populated upgrades and isolated backup/restore.
See [current checkpoint](mvp5-progress.md), [acceptance plan](mvp5-plan.md),
and [executed evidence](mvp5-verification.md).

MVP-4 /0.4.0 remains the verified previous release: [evidence](mvp4-verification.md).

# MVP-3 historical checkpoint

Previous release: **MVP-3 /0.3.0**, phases0–6 locally verified on2026-09-27. See [MVP-3 progress](mvp3-progress.md)
and [executed verification](mvp3-verification.md).

Seven independently deployable applications implement networking, media, private messaging, moderation and the hiring
workflow: companies/recruiters, jobs/search, idempotent applications with immutable snapshots, status/withdrawal and
generic notifications.60 tests pass with real Oracle/S3; Kafka recovery/replay, all release journeys, fresh Compose,
populated upgrades, retained kind rollout, rolling restarts and isolated backup/restore passed.

The original Compose stack is running with optional monitoring. The dedicated kind node is stopped with its data
retained; both targets bind8080/8180. The earlier Keycloak port allocation failure is avoided by running them serially.
Switching instructions remain in the [runbook](runbook.md#switching-between-compose-and-kind).

Hosted CI is NOT RUN. No external deployment/publication occurred. No unresolved local release blocker. Use the running
APIs or IntelliJ requests; do not start another release without a new request.

Historical evidence: [MVP-1](verification.md), [MVP-2](mvp2-verification.md). MVP-3 extends the retained MVP-2 cluster
and verifies its previous business data remains unchanged across the upgrade.

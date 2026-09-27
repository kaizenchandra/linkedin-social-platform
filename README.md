# professional-network-mvp

Java21 / Spring Boot4 backend with seven independently deployable applications: gateway, member, content, notification, media, messaging and hiring. Oracle owns durable data, Kafka carries transactional outbox events, Keycloak provides OIDC, and private S3-compatible storage holds normalized images.

MVP-2 adds avatars/post images, MEMBERS/CONNECTIONS visibility, bidirectional blocking, private one-to-one text messaging, and audited reporting/moderation. Blocking and visibility use current authoritative policy. Message history remains available to participants after blocking/disconnection; new sends are denied.

MVP-3 adds unverified company pages, owner/recruiter roles, invitations, jobs/search, immutable application snapshots, status history/withdrawal, hiring notifications and audited job moderation. Current company membership controls reviewer access; personal blocking remains separate.

MVP-4 adds independent member/company follows, a chronological feed with current authorization, private saved posts/jobs, explainable suggestions, and explicitly enabled saved-search job alerts. Matching checkpoints, Oracle uniqueness and notification eligibility checks provide restartable at-least-once delivery. See [discovery and alert semantics](docs/discovery-and-alert-semantics.md) and [verification](docs/mvp4-verification.md).

MVP-5 adds durable owner-scoped SSE for messaging and notifications, multi-device read synchronization and private conversation mute/archive. REST remains authoritative. See [replay protocol](docs/realtime-protocol.md) and [verification](docs/mvp5-verification.md).

## Run locally

Requires JDK21, Docker/Compose and Python3; allow roughly10GB Docker memory. Import root `pom.xml` in IntelliJ with JDK21. **Existing MVP-4 installations: follow the [MVP-5 upgrade procedure](docs/runbook.md#mvp-5-upgrade-and-operations)** to preserve credentials and apply the additive migrations and Kafka permissions.

Compose and kind both bind ports8080/8180. If the dedicated kind node is running, stop it first without deleting its data:

```sh
docker stop professional-network-mvp-control-plane
```

For a fresh setup (no existing `.env`):

```sh
python3 scripts/init-local.py
scripts/java21.sh -B -ntp clean verify
docker compose up -d --build --wait --wait-timeout 240
python3 scripts/auth-hiring-setup.py
python3 scripts/smoke-mvp4.py
```

Gateway: http://localhost:8080. Keycloak: http://localhost:8180. Run `python3 scripts/login.py` for interactive PKCE registration/login. Test setup provisions disposable identities through Keycloak Admin REST; ordinary users cannot self-assign moderator permissions. Secrets, test tokens and S3 identity files stay untracked.

`verify` uses real Oracle and S3 Testcontainers. The [runbook](docs/runbook.md) covers OrbStack discovery, populated upgrades, deployment switching, IntelliJ requests, telemetry, recovery and backups. Release artifacts are tagged 0.5.0; do not revert old binaries after new private content/event types exist.

MVP-5 is locally verified:84tests, MVP-1 through MVP-5 gateway journeys, fresh Compose, populated upgrades, two-pod kind replay, failure recovery, rolling restarts and isolated backup/restore. [Executed evidence and limitations](docs/mvp5-verification.md). Hosted CI has not been run.

## Design and evidence

- [Release progress](docs/mvp5-progress.md), [executed verification](docs/mvp5-verification.md), [acceptance plan](docs/mvp5-plan.md)
- [Hiring authorization](docs/hiring-authorization-matrix.md), [privacy decision table](docs/privacy-policy-matrix.md), [architecture](docs/architecture.md), [ADRs](docs/adr/), [technology decisions](docs/technology-decisions.md)
- [OpenAPI](contracts/openapi.json), [events](contracts/event-v1.schema.json), [networking requests](requests/journey.http), [hiring requests](requests/hiring.http)
- [MVP-1 historical evidence](docs/verification.md), [operations](docs/runbook.md), [roadmap](docs/roadmap.md)

Local single-node Oracle/Kafka/Keycloak/object storage does not establish production readiness, high availability or capacity guarantees. Remote CI results are separate from local checks; see the release verification record.

MVP-4 gateway journey and IntelliJ examples:

```sh
python3 scripts/auth-hiring-setup.py
python3 scripts/smoke-mvp4.py
```

Open [requests/mvp4.http](requests/mvp4.http). Follow next cursors even when a privacy-filtered page is empty. Company following does not grant recruiter access or enable alerts. Rule-based discovery uses no ML or private hiring/message history.

Live-update journey and development client:

```sh
python3 scripts/smoke-mvp5.py
python3 -m http.server 3000 --directory tools/realtime
```

Open localhost3000 and paste a current user token from the PKCE workflow; it remains in memory. Two independent cursors, explicit reads, bounded reconnect and reset handling are documented in [the protocol](docs/realtime-protocol.md). [IntelliJ examples](requests/mvp5.http) use private environment variables.

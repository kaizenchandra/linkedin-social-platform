# professional-network-mvp

Java21 / Spring Boot4 backend with seven independently deployable applications: gateway, member, content, notification, media, messaging and hiring. Oracle owns durable data, Kafka carries transactional outbox events, Keycloak provides OIDC, and private S3-compatible storage holds normalized images.

MVP-2 adds avatars/post images, MEMBERS/CONNECTIONS visibility, bidirectional blocking, private one-to-one text messaging, and audited reporting/moderation. Blocking and visibility use current authoritative policy. Message history remains available to participants after blocking/disconnection; new sends are denied.

MVP-3 adds unverified company pages, owner/recruiter roles, invitations, jobs/search, immutable application snapshots, status history/withdrawal, hiring notifications and audited job moderation. Current company membership controls reviewer access; personal blocking remains separate.

## Run locally

Requires JDK21, Docker/Compose and Python3; allow roughly10GB Docker memory. Import root `pom.xml` in IntelliJ with JDK21. **Existing installations: follow the [MVP-3 upgrade procedure](docs/runbook.md#mvp-3-upgrade-and-hiring-operations)** to preserve credentials and provision new schemas/clients.

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
python3 scripts/smoke-mvp3.py
```

Gateway: http://localhost:8080. Keycloak: http://localhost:8180. Run `python3 scripts/login.py` for interactive PKCE registration/login. Test setup provisions disposable identities through Keycloak Admin REST; ordinary users cannot self-assign moderator permissions. Secrets, test tokens and S3 identity files stay untracked.

`verify` uses real Oracle and S3 Testcontainers. The [runbook](docs/runbook.md) covers OrbStack discovery, populated upgrades, deployment switching, IntelliJ requests, telemetry, recovery and backups. Release artifacts are tagged0.3.0; do not revert old binaries after new private content/event types exist.

MVP-3 is locally verified:60 tests, all release journeys, fresh Compose, populated upgrades, retained kind deployment, Kafka recovery/replay, rolling restarts and isolated backup/restore. [Executed evidence and limitations](docs/mvp3-verification.md). Hosted CI has not been run.

## Design and evidence

- [Release progress](docs/mvp3-progress.md), [executed verification](docs/mvp3-verification.md), [acceptance plan](docs/mvp3-plan.md)
- [Hiring authorization](docs/hiring-authorization-matrix.md), [privacy decision table](docs/privacy-policy-matrix.md), [architecture](docs/architecture.md), [ADRs](docs/adr/), [technology decisions](docs/technology-decisions.md)
- [OpenAPI](contracts/openapi.json), [events](contracts/event-v1.schema.json), [networking requests](requests/journey.http), [hiring requests](requests/hiring.http)
- [MVP-1 historical evidence](docs/verification.md), [operations](docs/runbook.md), [roadmap](docs/roadmap.md)

Local single-node Oracle/Kafka/Keycloak/object storage does not establish production readiness, high availability or capacity guarantees. Remote CI results are separate from local checks; see the release verification record.

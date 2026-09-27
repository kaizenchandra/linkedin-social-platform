# professional-network-mvp

Java21 / Spring Boot4 backend with six independently deployable applications: gateway, member, content, notification, media and messaging. Oracle owns durable data, Kafka carries transactional outbox events, Keycloak provides OIDC, and private S3-compatible storage holds normalized images.

MVP-2 adds avatars/post images, MEMBERS/CONNECTIONS visibility, bidirectional blocking, private one-to-one text messaging, and audited reporting/moderation. Blocking and visibility use current authoritative policy. Message history remains available to participants after blocking/disconnection; new sends are denied.

## Run locally

Requires JDK21, Docker/Compose and Python3; allow roughly9GB Docker memory. Import root `pom.xml` in IntelliJ with JDK21. **Existing MVP-1 installations: follow the [upgrade procedure](docs/runbook.md#mvp-2-upgrade-and-local-operation)** to preserve credentials and provision new schemas/clients.

Compose and kind both bind ports8080/8180. If the dedicated kind node is running, stop it first without deleting its data:

```sh
docker stop professional-network-mvp-control-plane
```

For a fresh setup (no existing `.env`):

```sh
python3 scripts/init-local.py
scripts/java21.sh -B -ntp clean verify
docker compose up -d --build --wait --wait-timeout 240
python3 scripts/auth-test-setup.py
python3 scripts/auth-moderator-setup.py
python3 scripts/smoke-mvp2.py
```

Gateway: http://localhost:8080. Keycloak: http://localhost:8180. Run `python3 scripts/login.py` for interactive PKCE registration/login. Test setup provisions disposable identities through Keycloak Admin REST; ordinary users cannot self-assign moderator permissions. Secrets, test tokens and S3 identity files stay untracked.

`verify` uses real Oracle and S3 Testcontainers. The [runbook](docs/runbook.md) covers OrbStack discovery, populated upgrades, deployment switching, IntelliJ requests, telemetry, recovery and backups. Release artifacts are tagged0.2.0; do not revert to MVP-1 binaries after private content exists.

MVP-2 is locally verified: 32 automated tests, real Oracle/Kafka/S3 workflows, fresh Compose and kind deployments, populated migration and recovery checks. Remote CI has not been executed; detailed limits are in the verification record.

## Design and evidence

- [Release progress](docs/mvp2-progress.md), [executed verification](docs/mvp2-verification.md), [acceptance plan](docs/mvp2-plan.md)
- [Privacy decision table](docs/privacy-policy-matrix.md), [architecture](docs/architecture.md), [ADRs](docs/adr/), [technology decisions](docs/technology-decisions.md)
- [OpenAPI](contracts/openapi.json), [events](contracts/event-v1.schema.json), [IntelliJ requests](requests/journey.http)
- [MVP-1 historical evidence](docs/verification.md), [operations](docs/runbook.md), [roadmap](docs/roadmap.md)

Local single-node Oracle/Kafka/Keycloak/object storage does not establish production readiness, high availability or capacity guarantees. Remote CI results are separate from local checks; see the release verification record.

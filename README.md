# professional-network-mvp

A runnable Java 21 / Spring Boot 4 backend with independently deployable gateway, member, content and notification services. Oracle owns durable data, Kafka carries transactional outbox events, and Keycloak provides OIDC authentication.

MVP-1 includes profiles and experience, member search, connection requests, text posts, comments, likes, chronological connection feeds and polling notifications. All profiles and posts are visible to authenticated members; connections control feed inclusion only.

## Run locally

Requires JDK21, Docker/Compose and Python3; allow roughly 9 GB Docker memory. Import root `pom.xml` into IntelliJ and select JDK21.

Compose and the local kind cluster both use ports **8080 and 8180**. If you previously deployed kind, stop its dedicated node before starting Compose (the cluster and its data are retained):

```sh
docker stop professional-network-mvp-control-plane
```

On a fresh setup there is no kind node to stop. If `.env` already exists, skip `init-local.py`; it intentionally preserves existing secrets.

```sh
python3 scripts/init-local.py
scripts/java21.sh -B -ntp clean verify
docker compose up -d --build --wait --wait-timeout 240
python3 scripts/auth-test-setup.py
python3 scripts/check-pkce.py
python3 scripts/smoke.py
```

Gateway: http://localhost:8080. Keycloak: http://localhost:8180. Run `python3 scripts/login.py` for interactive registration/login using Authorization Code with PKCE. Test setup creates separate disposable test identities. Credentials and tokens stay in ignored local files.

`verify` uses real Oracle Testcontainers. See the [runbook](docs/runbook.md) for OrbStack socket settings, dedicated external Oracle tests, IntelliJ HTTP requests, telemetry, recovery and local kind deployment. Compose and kind share host ports; run them serially.

## Evidence and design

All local phases passed: 20 automated tests, real Compose/kind journeys, Kafka recovery/replay, Oracle backup/restore, traces, metrics and application image scans. Both deployment modes have been verified; see the current checkpoint for which one is running. Remote CI has not been executed. See [verification](docs/verification.md) and [current checkpoint](docs/progress.md) for exact scope and limitations.

- [Architecture and ownership](docs/architecture.md), [ADRs](docs/adr/), [technology decisions](docs/technology-decisions.md)
- [OpenAPI](contracts/openapi.json), [event schema](contracts/event-v1.schema.json), [IntelliJ requests](requests/journey.http)
- [Implementation plan](docs/implementation-plan.md), [operations](docs/runbook.md), [roadmap](docs/roadmap.md)

Single-node local Oracle/Kafka/Keycloak are development infrastructure. Results do not establish production readiness, high availability or capacity guarantees.

# Local operations

## Compose and IntelliJ

Requirements: Java21, Docker/Compose, Python3; allow roughly9GB Docker memory. Import root `pom.xml` into IntelliJ and choose JDK21. `scripts/java21.sh` selects Java21 on macOS; elsewhere set JAVA_HOME. Core startup does not require telemetry.

```sh
python3 scripts/init-local.py
scripts/java21.sh -B -ntp clean verify
docker compose up -d --build --wait --wait-timeout 240
python3 scripts/auth-test-setup.py
python3 scripts/check-pkce.py
python3 scripts/smoke.py
python3 scripts/http-env.py
```

Open `requests/journey.http`, select the `local` environment. Generated private HTTP environment and `.env` must remain untracked. Testcontainers runs real Oracle23.9, no H2 business-database substitute. On OrbStack, if discovery fails set `DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. `verify-oracle-local.py` uses only the dedicated Compose schemas as an alternative and tests populated migrations.

Runtime ports: gateway8080; Keycloak8180; direct business debugging8081–8083, all loopback-bound. Runtime services use per-service DML-only accounts. Dedicated migration containers use schema-owner credentials and complete before application health gates. Do not supply owner credentials to runtime pods. Pools are8 connections per business replica: total24 at one replica/service, plus relay/migration/admin headroom. Oracle is shared local infrastructure, not HA.

## Identity and security

Run `python3 scripts/login.py` for interactive login/registration. It opens Keycloak and captures the localhost8765 callback with S256 PKCE and state validation; tokens are stored in an owner-only `.local/interactive-token.json`. Passwords remain in Keycloak. `network-web` has no password grant. Test provisioning creates uniquely named disposable test-only direct-grant clients and three identities through Admin REST; these are for the repeatable acceptance scripts only.

OIDC discovery: http://localhost:8180/realms/network/.well-known/openid-configuration . Refresh at the discovered token endpoint with `grant_type=refresh_token`, `client_id`, `refresh_token`. Logout via the end-session endpoint or POST logout with client_id and refresh_token. Logout invalidates the refresh session, but independently validated access JWTs may remain usable until their 300-second lifetime ends, with Spring Security's default 60-second clock-skew tolerance. Logout does not immediately revoke an already issued access JWT. `check-pkce.py` demonstrates code exchange/refresh/logout. `refresh-session.py` renews smoke tokens without changing identities.

All services independently validate signature, issuer, audience, expiry and UUID subject. Internal connections lookup requires `connections.read`, issued only to the content service client. Metrics require `metrics.read`. Health endpoints expose status only on separate management ports. Stateless bearer APIs do not authenticate with cookies, so CSRF is disabled. CORS has one explicit local origin. Unknown identity fields are rejected; gateway ignores forged identity headers. Request bodies, including chunked bodies, are limited to64KiB at the gateway. Tokens and content are not intentionally logged; producer failure logging omits event payloads.

Kafka uses separate principals and explicit topic/group ACLs. Only the admin principal provisions/replays. Local SASL_PLAINTEXT and HTTP are for loopback/private-container traffic; production requires TLS, managed secrets, non-development Keycloak and network controls. The local setup is not an internet deployment.

## Telemetry

```sh
scripts/fetch-agent.sh
python3 scripts/prepare-observability.py
docker compose -f compose.yaml -f compose.observability.yaml up -d --build --wait
python3 scripts/auth-test-setup.py
python3 scripts/smoke.py
python3 scripts/check-telemetry.py
python3 scripts/load.py --seconds 20 --concurrency 4
```

Zipkin9411, Prometheus9095, Grafana3001. Grafana user `admin`, password in `.env`. Prometheus uses a scoped OAuth client; its local secret is mode0600 and container UID matches the file owner. OpenTelemetry agent is the sole tracing instrumentation path; no Brave. Trace context is persisted with outbox rows and restored for Kafka publication. IDs are log/trace fields, never metric labels. The dashboard covers request rate/latency/errors, database pools, outbox backlog/failures and consumer processing/failures. Tracing is optional and basic startup works without it.

## Events, failure and replay

Business changes/outbox rows share an Oracle transaction. The relay locks at most5 pending rows using SKIP LOCKED, waits for broker acknowledgement and only then marks delivery. A crash after acknowledgement can duplicate publication. Notification effects and consumed-event IDs commit atomically before Kafka offsets. Three1-second retries precede explicit `network.events.v1.DLT` routing. If DLT publication fails, the source record remains unacknowledged. Notifications represent historical actions; out-of-order events never reconstruct relationship state. Dedup rows must be retained while replay remains possible.

```sh
python3 scripts/check-event-recovery.py  # disruptive: dedicated Compose only
python3 scripts/check-dead-letter.py
```

To replay operationally, inspect the DLT with the Kafka admin console consumer using `--consumer.config /tmp/admin.properties`. Save the original envelope in an access-restricted local file, fix the cause/schema, and send it to `network.events.v1` with the same eventId and aggregateId message key using the producer's `--producer.config /tmp/admin.properties --property parse.key=true` (input: aggregateId, tab, JSON). Never generate a new eventId merely to retry. Scripts demonstrate both duplicate replay and repair of a rejected event. Do not reset the entire consumer group to repair one record.

## Persistence, backup and rollback

```sh
python3 scripts/refresh-session.py
python3 scripts/check-restart.py compose
python3 scripts/check-backup-restore.py
```

The Data Pump check uses a flashback SCN, exports all service schemas inside the local Oracle volume, imports into three fresh PNM_R_* schemas, compares every business table count and profile/post text, then drops only those temporary schemas. Run it while application writes are quiescent. Backups contain personal data and belong in access-controlled storage; do not commit or publish them. Local evidence demonstrates this procedure, not disaster-recovery guarantees or identity-provider recovery.

`docker compose stop` retains data. Never use `down -v` unless deliberately discarding the dedicated local dataset. Oracle, Kafka and Keycloak have persistent volumes. Backup before schema changes. Migration V4 converts member summaries to CLOB while preserving data; an older application expecting VARCHAR cannot be assumed to validate after rollback. Prefer forward fixes. Application image rollback does not reverse schemas, broker offsets or side effects.

## Dedicated kind cluster

```sh
scripts/install-kind.sh
docker compose -f compose.yaml -f compose.observability.yaml stop
scripts/deploy-kind.sh
python3 scripts/auth-test-setup.py
python3 scripts/check-pkce.py
python3 scripts/smoke.py
python3 scripts/check-kind-events.py
python3 scripts/refresh-session.py
python3 scripts/check-restart.py kind
```

Run the Compose build first so all pinned infrastructure and application images exist locally. The deployment script requires a Docker version supporting `image save --platform` (verified with Docker29.4). It creates local tag aliases from pinned digest references and exports only the host platform to avoid incomplete multi-platform indexes. Oracle has a guarded init container that seeds a fresh PVC from the faststart image; existing database files are retained. Kubernetes does not perform Docker's initial named-volume copy automatically.

Only `professional-network-mvp` is targeted. The script writes `.local/kubeconfig`, never the user's default context. All kubectl operations use `scripts/kubectl-local.sh`. Infrastructure has PVCs; application pods have resources, startup/readiness/liveness probes, non-root users and graceful shutdown. Migration and Kafka ACL jobs finish before application rollout. Initial images are loaded locally; nothing is published externally. Oracle/Kafka/Keycloak are single-node local deployments, not HA. A kind cluster deletion destroys its node-local PVC storage; export backups first. For a subsequent release, use a new immutable image version and uniquely versioned migration jobs before changing Deployments; do not silently rerun completed Job names.

```sh
scripts/kubectl-local.sh apply --dry-run=server -f infra/k8s/applications.json
scripts/kubectl-local.sh -n network-mvp get pods,pvc,jobs
```

Structural `check-manifests.py` is not equivalent to server validation or deployment. Actual results are in verification.md.

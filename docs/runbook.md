# Local operations

## Compose and IntelliJ

Requirements: Java21, Docker/Compose, Python3; allow roughly9GB Docker memory. Import root `pom.xml` into IntelliJ and choose JDK21. `scripts/java21.sh` selects Java21 on macOS; elsewhere set JAVA_HOME. Core startup does not require telemetry.

```sh
python3 scripts/init-local.py
scripts/java21.sh -B -ntp clean verify
docker compose up -d --build --wait --wait-timeout 240
python3 scripts/auth-test-setup.py
python3 scripts/check-pkce.py
python3 scripts/auth-moderator-setup.py
python3 scripts/smoke-mvp2.py
python3 scripts/http-env.py
```

Open `requests/journey.http`, select the `local` environment. Generated private HTTP environment and `.env` must remain untracked. Testcontainers runs real Oracle23.9, no H2 business-database substitute. On OrbStack, if discovery fails set `DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. `verify-oracle-local.py` uses only the dedicated Compose schemas as an alternative and tests populated migrations.

Runtime ports: gateway8080; Keycloak8180; direct business debugging8081–8085, all loopback-bound. Runtime services use per-service DML-only accounts. Dedicated migration containers use schema-owner credentials and complete before application health gates. Do not supply owner credentials to runtime pods. Pools are8 connections per business replica: total40 at one replica/service, plus relay/migration/admin headroom. Oracle is shared local infrastructure, not HA.

## Switching between Compose and kind

Both environments bind gateway port8080 and Keycloak port8180. A `port is already allocated` error can mean the other environment is still running. Confirm the owner with `docker ps --format '{{.Names}}\t{{.Ports}}'`.

Switch from this project's kind cluster to Compose:

```sh
docker stop professional-network-mvp-control-plane
docker compose up -d --build --wait --wait-timeout 240
```

If Keycloak reports healthy after a failed bind but `docker compose ps` shows no published port for it, recreate only that container to restore the binding (its named data volume is preserved):

```sh
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 120 keycloak
```

Switch back to an existing, stopped kind node:

```sh
docker compose -f compose.yaml -f compose.observability.yaml stop
docker start professional-network-mvp-control-plane
scripts/kubectl-local.sh -n network-mvp get pods,pvc,jobs
```

Allow Kubernetes workloads to become ready before using the APIs. These commands preserve both environments' separate datasets. Do not delete the kind cluster or run `down -v` merely to free ports. Sessions belong to each environment's Keycloak instance; log in again after switching. For the acceptance scripts, regenerate the test session with `auth-test-setup.py`, `auth-moderator-setup.py`, then `smoke-mvp2.py`.

## Identity and security

Run `python3 scripts/login.py` for interactive login/registration. It opens Keycloak and captures the localhost8765 callback with S256 PKCE and state validation; tokens are stored in an owner-only `.local/interactive-token.json`. Passwords remain in Keycloak. `network-web` has no password grant. Test provisioning creates uniquely named disposable test-only direct-grant clients and three identities through Admin REST; these are for the repeatable acceptance scripts only.

OIDC discovery: http://localhost:8180/realms/network/.well-known/openid-configuration . Refresh at the discovered token endpoint with `grant_type=refresh_token`, `client_id`, `refresh_token`. Logout via the end-session endpoint or POST logout with client_id and refresh_token. Logout invalidates the refresh session, but independently validated access JWTs may remain usable until their 300-second lifetime ends, with Spring Security's default 60-second clock-skew tolerance. Logout does not immediately revoke an already issued access JWT. `check-pkce.py` demonstrates code exchange/refresh/logout. `refresh-session.py` renews smoke tokens without changing identities.

All services independently validate signature, issuer, audience, expiry and UUID subject. Internal connections lookup requires `connections.read`, issued only to scoped content, media and messaging service clients. Metrics require `metrics.read`. Health endpoints expose status only on separate management ports. Stateless bearer APIs do not authenticate with cookies, so CSRF is disabled. CORS has one explicit local origin. Unknown identity fields are rejected; gateway ignores forged identity headers. Request bodies, including chunked bodies, are limited to64KiB at the gateway, except bounded multipart media uploads (6MiB envelope). Tokens and content are not intentionally logged; producer failure logging omits event payloads.

Kafka uses separate principals and explicit topic/group ACLs. Only the admin principal provisions/replays. Local SASL_PLAINTEXT and HTTP are for loopback/private-container traffic; production requires TLS, managed secrets, non-development Keycloak and network controls. The local setup is not an internet deployment.

## Telemetry

```sh
scripts/fetch-agent.sh
python3 scripts/prepare-observability.py
docker compose -f compose.yaml -f compose.observability.yaml up -d --build --wait
python3 scripts/auth-test-setup.py
python3 scripts/auth-moderator-setup.py
python3 scripts/smoke-mvp2.py
python3 scripts/check-telemetry.py
python3 scripts/load-mvp2.py --seconds 20 --concurrency 4
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
python3 scripts/check-mvp2-restart.py compose
python3 scripts/check-mvp2-backup.py
python3 scripts/check-object-backup.py
```

The Data Pump check uses a flashback SCN, exports all service schemas inside the local Oracle volume, imports into five fresh PNM_R_* schemas, compares every business table count and profile/post text, then drops only those temporary schemas. Run it while application writes are quiescent. Backups contain personal data and belong in access-controlled storage; do not commit or publish them. Local evidence demonstrates this procedure, not disaster-recovery guarantees or identity-provider recovery.

`docker compose stop` retains data. Never use `down -v` unless deliberately discarding the dedicated local dataset. Oracle, Kafka, Keycloak and object storage have persistent volumes. Backup before schema changes. Migration V4 converts member summaries to CLOB while preserving data; an older application expecting VARCHAR cannot be assumed to validate after rollback. Prefer forward fixes. Application image rollback does not reverse schemas, broker offsets or side effects.

## Dedicated kind cluster

```sh
scripts/install-kind.sh
docker compose -f compose.yaml -f compose.observability.yaml stop
scripts/deploy-kind.sh
python3 scripts/auth-test-setup.py
python3 scripts/check-pkce.py
python3 scripts/auth-moderator-setup.py
python3 scripts/smoke-mvp2.py
python3 scripts/check-kind-events.py
python3 scripts/refresh-session.py
python3 scripts/check-mvp2-restart.py kind
```

Run the Compose build first so all pinned infrastructure and application images exist locally. The deployment script requires a Docker version supporting `image save --platform` (verified with Docker29.4). It creates local tag aliases from pinned digest references and exports only the host platform to avoid incomplete multi-platform indexes. Oracle has a guarded init container that seeds a fresh PVC from the faststart image; existing database files are retained. Kubernetes does not perform Docker's initial named-volume copy automatically.

Only `professional-network-mvp` is targeted. The script writes `.local/kubeconfig`, never the user's default context. All kubectl operations use `scripts/kubectl-local.sh`. Infrastructure has PVCs; application pods have resources, startup/readiness/liveness probes, non-root users and graceful shutdown. Migration and Kafka ACL jobs finish before application rollout. Initial images are loaded locally; nothing is published externally. Oracle/Kafka/Keycloak are single-node local deployments, not HA. A kind cluster deletion destroys its node-local PVC storage; export backups first. For a subsequent release, use a new immutable image version and uniquely versioned migration jobs before changing Deployments; do not silently rerun completed Job names.

```sh
scripts/kubectl-local.sh apply --dry-run=server -f infra/k8s/applications.json
scripts/kubectl-local.sh -n network-mvp get pods,pvc,jobs
```

Structural `check-manifests.py` is not equivalent to server validation or deployment. Actual release results are in mvp2-verification.md.

## MVP-2 upgrade and local operation

Release0.2.0 adds media and messaging. On an existing MVP-1 checkout, retain the original `.env` and volumes:

```sh
python3 scripts/init-mvp2.py
scripts/java21.sh -B -ntp clean verify
docker compose stop api-gateway member-service content-service notification-service
docker compose up -d --wait oracle kafka keycloak object-store
python3 scripts/upgrade-mvp2.py
docker compose up -d --build --wait --wait-timeout 240
python3 scripts/auth-test-setup.py
python3 scripts/auth-moderator-setup.py
python3 scripts/smoke-mvp2.py
```

Fresh setups use `init-local.py`, which generates both releases' secrets and the private S3 identity file, then build/verify and `docker compose up` as usual. Upgrade provisioning is idempotent and creates only missing schemas/clients/role configuration; it does not rotate existing credentials or grant users moderation. Long-running Compose containers use `unless-stopped` so database startup races recover; migration/ACL jobs remain one-shot.

The maintenance window prevents old binaries from ignoring new privacy/moderation fields. Do not roll back to MVP-1 after private content exists. Prefer a forward fix; restoring a coordinated pre-upgrade backup loses subsequent writes. Application rollback never reverses schema changes. `check-mvp2-upgrade.py` creates isolated populated MVP-1 schemas, migrates them with the release images, verifies data/defaults and removes only its fixtures.

New direct debugging ports are8084(media),8085(messaging),8333(private S3), all loopback. Applications use independent DML credentials; migrations use owners. Five business pools ×8 connections =40, plus workers/migrations/admin headroom. Run kind and Compose serially. The media S3 credential is restricted to network-media; separate local admin credentials are used only for setup/backup. SeaweedFS auxiliary ports are not host-published. Production requires TLS, service network isolation, hardened identity/database/object storage and managed secret rotation.

Upload a multipart `file` to `/api/v1/media`; only decoded JPEG/PNG bytes are accepted. Files are limited to5MiB input/output,16MP and8192 per dimension; original metadata is removed. The gateway admits at most two buffered upload requests per instance; media allows one decoder per instance and a persisted40/hour uploader quota (concurrent replicas may overshoot). These are bounded local abuse controls, not global distributed limits.

Edit attachments with `POST /api/v1/media/attachments`: a client UUID operationId, resourceType PROFILE/POST, owned resourceId and complete mediaIds list. Reuse an operationId only for identical retries. Empty list removes references. Profiles expose avatarMediaId; posts expose mediaIds. Downloads use the authorized `/api/v1/media/{id}/content` stream with no-store; READY uploads cannot be downloaded normally. Never expose raw storage URLs. Claims, references and cleanup use the durable owner protocol in ADR008. Default cleanup grace24h, minimum10min;25 rows per sweep. A failed dependency preserves bytes and metadata for retry. EXIF orientation is not applied; accepted images are decoded and re-encoded as stored pixels.

Messages require accepted unblocked connections for new sends. History remains accessible to the two participants after disconnection/blocking. clientMessageId retries return the original result even after blocking; altered text conflicts. Poll using the returned nextCursor and mark read with a messageId from the conversation. The database enforces60 new sends/sender/conversation/minute. Moderator authority does not grant message access.

Only identity-provider administrators assign the `moderator` realm role. Moderator inspection/hide/restore/dismiss endpoints require an audit reason. Report APIs never reveal reporter identities. Author deletion is now a retained tombstone; inspection redacts deleted text, and restore cannot resurrect it. Historical notifications are generic IDs/types; clients fetch targets through their authorized APIs and display unavailable on404. No durable content previews are embedded.

## MVP-2 recovery and backup checks

```sh
python3 scripts/refresh-session.py
python3 scripts/check-mvp2-failures.py       # dedicated Compose only; restores dependencies
python3 scripts/check-mvp2-replay.py         # or: kind
python3 scripts/check-mvp2-restart.py database
python3 scripts/check-s3-permissions.py
```

For a quiescent backup/restore check, stop the six application containers while Oracle/object-store remain running, then run `check-mvp2-backup.py` and `check-object-backup.py`. The former uses Data Pump/flashback SCN and five isolated restore schemas, comparing table counts, text and message read positions. The latter exports bounded private objects to owner-only `.local/backups`, restores to a fresh temporary bucket, compares SHA-256, and removes that bucket. Original schemas/objects remain intact. Treat database and object snapshots as one coordinated backup set; protect backup credentials and personal data. These checks do not establish Keycloak disaster recovery, retention compliance or complete erasure.

For kind, stop Compose including its optional observability services, then run `scripts/deploy-kind.sh`. It resumes an existing stopped dedicated node, loads six0.2.0 images plus pinned infrastructure, scales down old applications, applies infrastructure/secret references, provisions missing schemas/identity clients, runs uniquely named MVP-2 migration/ACL jobs, then starts the six applications. Test with the same smoke script, `check-mvp2-replay.py kind`, `check-kind-events.py`, and `check-mvp2-restart.py kind`. All operations use the explicit local kubeconfig. Do not delete existing PVCs to perform an upgrade.

Prometheus now scrapes six scoped targets and loads reliability alert rules; Grafana includes media storage/reconciliation/cleanup panels. Alerts are local rules only; no external notification receiver is configured. The optional agent remains the sole tracing path. See `docs/mvp2-verification.md` for executed evidence rather than inferring status from these commands.

MVP-2 retention: messages, read positions, moderation reports/audits, owner attachment operation records, notifications and consumer deduplication records have no automatic expiry in this release. Author-deleted posts/comments remain tombstoned, with normal reads denied; message deletion/account erasure are outside scope. Unreferenced objects are eligible after the configured grace measured from their last media-state update; a removed reference denies download immediately, but physical deletion waits for an eligible successful reconciliation. This is not a guaranteed delay measured from detachment, and outages extend retention. Backups have operator-managed retention. Define lawful retention, deletion workflows and backup expiry before production use; do not promise complete erasure.

## MVP-3 upgrade and hiring operations

Release0.3.0 adds hiring-service (loopback8086), a sixth service-owned schema and new scoped identity/Kafka credentials. Keep `.env` and persisted volumes. For an existing MVP-2 installation:

```sh
python3 scripts/init-mvp3.py
scripts/java21.sh -B -ntp clean verify
docker compose stop api-gateway member-service content-service notification-service media-service messaging-service hiring-service
docker compose up -d --wait oracle kafka keycloak object-store
python3 scripts/upgrade-mvp3.py
docker compose up -d --build --wait --wait-timeout 240
python3 scripts/auth-hiring-setup.py
python3 scripts/smoke-mvp3.py
```

Fresh setup uses `init-local.py`, which generates all release secrets, then the root verification and Compose startup commands. Never overwrite an existing `.env` to upgrade. A maintenance window prevents the old notification consumer rejecting new hiring event types. Company logo media also requires the new media binary. Additive Oracle migrations preserve old rows; application rollback does not undo schema or event-format evolution. Use a forward fix or coordinated pre-upgrade restore; restoring loses later writes. `check-mvp3-upgrade.py` verifies populated isolated MVP-2 schemas with release migration images and removes only its fixtures.

Company pages are explicitly UNVERIFIED. Only the owner edits details/logos, invites/removes recruiters or transfers ownership to an accepted member. The old owner becomes a recruiter. Company memberships are checked from Oracle on each protected request, not token claims. Maximum100 accepted members/company and100 pending invitations; invitations expire after7 days. Company/job writes and authorized application operations use company locks, then resource locks. This intentionally favors clear concurrency semantics; measure contention before changing it.

Applications require an initialized profile and applicant-scoped UUID idempotency key. Identical retries return the original application, including its frozen snapshots; changed input conflicts. The profile revision is fetched through `hiring.profiles`; missing dependency returns503. Job closure/hiding and submissions serialize: whichever obtains the company/job locks first determines whether submission commits or fails409. Published deadlines are checked synchronously. Personal blocking does not revoke company application-review access, but continues to govern personal APIs and messaging.

Notification targets remain generic IDs/types. Clients open invitation/application targets through the corresponding authorized API and show unavailable on404; do not cache cover notes or previews. Submission events resolve current company recipients using `hiring.recipients` during consumption. Removed recruiters are excluded at lookup; later removal revokes target access even if a generic notification exists. New company members may receive an older queued event. Failed lookup retries with the existing bounded consumer backoff, then DLT. To replay: inspect the envelope without personal content, repair the dependency/schema problem, publish the original eventId and aggregate key to `network.events.v1`; never mint a new ID for a retry. `check-mvp3-recovery.py` exercises this with a real lookup outage, same-ID replay, broker outage and competing relays.

```sh
TEST_SESSION=.local/hiring-session.json python3 scripts/refresh-session.py
python3 scripts/check-mvp3-recovery.py       # dedicated Compose only; restores stopped services
python3 scripts/check-mvp3-restart.py        # or kind
python3 scripts/check-mvp3-upgrade.py
python3 scripts/load-mvp3.py --jobs 60 --seconds 20 --concurrency 4
TEST_SESSION=.local/hiring-session.json python3 scripts/http-env.py
```

Choose `local` in IntelliJ and use `requests/hiring.http`. Each required actor needs a profile; `smoke-mvp3.py` initializes all test profiles. Test identities use the supported Keycloak admin setup; interactive users continue to use Authorization Code+PKCE. Platform moderator roles are administrator-assigned and separate from company roles. Job inspection/moderation is explicitly audited and grants no private-message access.

For backup verification stop all seven application services while Oracle/object storage remain running, then run `check-mvp3-backup.py` and `BACKUP_EVIDENCE=docs/mvp3-object-backup-evidence.json python3 scripts/check-object-backup.py`. Data Pump restores six schemas into isolated fixtures and compares table counts, application snapshots/statuses, personal content and message read positions. Keep Oracle/object/Keycloak backup policies coordinated. The versioned [hiring retention policy](hiring-retention-policy.json) is operator-managed; no automated purge is implied. Withdrawal immediately redacts recruiter-facing cover/profile responses; stored history, applicant access, previously viewed data and backups remain.

For kind, stop Compose and optional observability first to release8080/8180, then run `scripts/deploy-kind.sh`. The current script loads seven 0.4.0 images, preserves existing PVCs, provisions required schemas/scopes, completes uniquely named MVP-4 migration/ACL jobs, and rolls out applications using `.local/kubeconfig`. Run `auth-hiring-setup.py`, `smoke-mvp3.py` and `check-mvp3-restart.py kind`. Do not delete a retained cluster to upgrade it. The optional Compose monitoring profile scrapes seven authenticated targets, includes hiring latency/5xx panels and alerts, and traces HTTP plus outbox/Kafka. It is separate from the basic kind deployment.

Six business pools ×8 =48 connections per single-replica stack; budget additional replicas, rolling overlap, relay transactions, migration and admin connections. Current local deployment is single-node, with no HA, employer-verification, legal-compliance or production-capacity claim. See mvp3-verification.md for actual executed deployment and recovery evidence.

The local Zipkin memory profile retains at most10000 spans with a256MiB heap in a512MiB container. Older traces are evicted and a backend restart loses them; this is intentional bounded development storage. The default image heap exhausted during MVP-3 load testing before this configuration was added. Keep trace sampling/storage policy explicit when measuring or deploying elsewhere.

## MVP-4 upgrade and operations

Use the same seven services and existing secrets. Artifact version0.4.0; no infrastructure/library upgrades. From MVP-3:

```sh
python3 scripts/verify-oracle-local.py   # disposable schemas, no live-worker interference
# Maintenance window; preserve every volume and .env.
docker compose stop api-gateway member-service content-service notification-service media-service messaging-service hiring-service
docker compose build
docker compose run --rm kafka-init
docker compose up -d --wait notification-service
docker compose up -d --wait --wait-timeout 240
python3 scripts/auth-hiring-setup.py
python3 scripts/smoke-mvp4.py
```

Migrations add member7, content8, hiring8–11 and notification5; media/messaging schemas stay unchanged. No old published jobs are backfilled into publication snapshots or alerts. Deploy the new consumer before hiring starts publishing new types; the maintenance window also prevents old block handlers leaving follows behind. Application rollback does not reverse migrations or queued events. Prefer a forward fix; a coordinated pre-upgrade restore loses subsequent writes. Do not run old consumers against the expanded event stream.

Member/company follows each cap at500; connections retain their existing500 limit. Following gives no connection, messaging or company role. Blocking deletes both directions of follows and removes connections atomically in member-service. Bookmarks are private and confer no visibility. Feed/bookmark cursors may return an empty page with a next cursor; clients must continue from that cursor. Page relationships are current, not a frozen snapshot. Saved closed/expired jobs return only safe summaries; hidden jobs are omitted. No inaccessible bookmark counts are returned.

Saved searches cap at10/member and20 explicit companies/search. Keywords are case-insensitive literal AND terms (max8,100 characters), normalized whitespace; location is a literal substring. `%`/`_` are escaped. No indexed full-text claim. Company-follow filtering is interactive only; alert company selections are fixed IDs. Create/edit/re-enable is prospective, using the serialized eligibility epoch. A criteria change invalidates unfinished work for its prior version. The deterministic winning search is the smallest eligible matching search ID; disabling that winner conservatively suppresses queued delivery even if another overlapping search remains enabled. No replacement/backfill alert is created.

`/api/v1/notifications/preferences/job-alerts` reports effective consent plus the global setting. Default global permission creates nothing until an explicitly enabled search matches. Global disable affects job alerts only. Local preference updates serialize with notification persistence. The successful remote match/search/job eligibility check is the cancellation boundary for delivery already in progress; later cross-service changes cannot atomically recall a notification. Already delivered notifications remain generic identifiers; opening `/jobs/{id}` reauthorizes current visibility/deadline. A publisher may receive an alert they explicitly opted into; no unnecessary actor notification is generated otherwise.

Matching uses at most25 transactions/tick, each handling one owner's at-most10 searches, with durable owner cursor and unique(job,member) match. `network.alerts.delay` defaults1000ms; `network.alerts.enabled=false` pauses scheduling without deleting work. Kafka publication ingestion only creates durable work. Outbox backlog1000 applies backpressure. Inspect low-cardinality backlog, oldest age, FAILED work, retry/DLT and scanned/returned panels. Failure details are categories, never search/job text. Three Kafka retries precede DLT; five matcher failures precede FAILED. Repair the cause before replay.

- Publication ingestion DLT: `network.hiring.v1.DLT`; notification delivery DLT: `network.events.v1.DLT`. Use the authenticated admin tools described above, preserving eventId, aggregate key and envelope. Validate against contracts/event-v1.schema.json; do not mint replacement IDs. Publication replay resumes the existing work item; it does not reset DONE or the cursor.
- Failed matcher: a platform moderator/operator calls `POST /api/v1/hiring/moderation/alerts/{jobId}/replay`. Only FAILED changes to PENDING; checkpoint and match uniqueness remain. Ordinary members receive403.
- Notification eligibility/consent endpoints use the existing narrow `hiring.recipients` service scope, assigned to notification-internal. Member tokens and generic personal-policy scope do not authorize them. Dependency errors retry; no default authorization on failure.

Deleted searches retain a private tombstone; hidden/deleted saved resources retain only service-owned references for unsave/approved future cleanup. Publication snapshots, match records and deduplication rows are retained for replay. No automatic purge or complete-erasure claim; define retention before introducing cleanup, and include backups in that policy.

```sh
python3 scripts/check-mvp4-recovery.py       # disruptive dedicated Compose fixtures only
.local/venv/bin/python scripts/check-mvp4-contracts-security.py
python3 scripts/load-mvp4.py               # bounded 20s, concurrency 4; records actual tracing configuration
python3 scripts/check-mvp4-upgrade.py      # isolated populated MVP-3 schemas
python3 scripts/check-mvp4-restart.py       # or kind
# Stop apps before this exact-state backup comparison:
python3 scripts/check-mvp4-backup.py
python3 scripts/check-mvp4-fresh-compose.py # temporary project/volumes; restores original stack
python3 scripts/check-mvp4-kind-release.py  # dedicated retained cluster; restores Compose on exit
```

Use requests/mvp4.http in IntelliJ. Long test runs should refresh supported test sessions, not extend token lifetime. Enable the optional observability Compose file before `check-mvp4-telemetry.py`; basic Compose deliberately has no trace agent. Kind uses uniquely named MVP-4 migration/ACL jobs and seven0.4.0 images. Stop Compose before starting the retained dedicated kind node to release8080/8180; never stop unrelated listeners. `MVP4_BACKEND=kind python3 scripts/smoke-mvp4.py` runs the gateway journey and its notification restart using the dedicated kubeconfig. These remain single-node development deployments.

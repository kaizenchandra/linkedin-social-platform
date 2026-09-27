# Technology decisions

Verified 2026-09-27. Exact transitive and Maven plugin versions are pinned by the Boot parent/BOM; `./mvnw help:effective-pom` is the resolved inventory. No latest/snapshots/ranges.

| Technology | MVP status | Purpose | Compatibility / availability | Adoption trigger |
|---|---|---|---|---|
| Java 21 | Required | Application runtime | Host JDK21 selected explicitly; container Temurin21.0.12.1_1 | Now |
| Spring Boot 4.0.8 / Cloud 2025.1.3 | Required | BOM-managed MVC, security, gateway | Official matrix supports 4.0.x; compile and runtime gates | Now |
| MVC / JPA / Hibernate | Required | Blocking business persistence | Boot-managed Hibernate and Oracle dialect | Now |
| WebFlux / Gateway | Required | Nonblocking edge only | No JPA dependency in gateway | Now |
| Oracle JDBC 23.9.0.25.07 / Oracle Free23.9 | Required | Service-owned durable data | ARM64 image confirmed; real Oracle tests | Now |
| Flyway11.14.1 | Required | Versioned SQL migrations | Boot BOM; Oracle module; no H2 substitution | Now |
| Kafka4.1.2 / Spring Kafka4.0.7 / Spring Messaging | Required | Outbox and notifications | KRaft single-node local; at-least-once | Now |
| Keycloak26.7.4 | Required | OIDC PKCE and service credentials | Official image ARM64; development mode only locally | Now |
| Maven3.9.11 / Wrapper3.3.4 | Required | Repeatable build | Checksummed distribution; Java21 | Now |
| JUnit / Mockito / Testcontainers2.0.5 | Required | Domain/security/Oracle tests | Boot BOM manages versions; Oracle Free module | Now |
| Docker / Compose / kind / Kubernetes | Required | Local packaging and deployment | Host Docker29.4, Compose5.1, kubectl1.37; kind0.33.0 / Kubernetes1.37.0 | Now |
| Prometheus / Grafana / OpenTelemetry / Zipkin | Required | Metrics dashboards and HTTP/Kafka traces | Prometheus3.15.0, Grafana13.2.2, OTel agent2.31.1, Zipkin3.5.1; optional runtime profile | Phase5 |
| Spring Batch | Deferred | Restartable bulk work | Avoid unused batch runtime | Actual restartable bulk/scheduled requirement |
| Spring Integration | Deferred | Adapter flows | No current integration flow requirement | Concrete adapter orchestration |
| Scala / Play Framework | Deferred experiment | Alternative service evaluation | Verify Java/build interoperability separately | Isolated future experiment |
| Rest.li | Deferred experiment | Compatibility evaluation | Verify maintenance and Java/build compatibility first; no claim of LinkedIn internal equivalence | Isolated integration need |
| gRPC | Deferred | Internal RPC | Additional contracts and transport not justified | Measured communication bottleneck |
| GraphQL | Deferred | Client query edge | Additional authorization/query-cost controls needed | Real client query requirements |
| JavaScript / Node.js | Optional tooling | Test/load tools | No second application stack | Tooling need |
| Python | Development tooling | Smoke/setup/contract scripts | Python3 stdlib; no backend runtime | Now for local tooling |
| Ruby / C++ | Deferred | No MVP purpose | Additional toolchains | Concrete requirement |
| Spark / Hadoop / HDFS | Deferred | Offline analytics | Substantial infrastructure | Analytics pipeline requirement |
| Samza | Deferred experiment | Stream processing | Kafka consumers suffice | Stateful streaming requirement |
| Pinot | Deferred | Real-time analytics | No analytics query workload | Measured analytics need |
| Espresso / Voldemort / Couchbase | Deferred evaluation | Alternative storage | Verify exact public project availability; do not equate names with proprietary systems | Storage requirement unmet by Oracle |
| Apache Helix | Deferred evaluation | Cluster coordination | Kubernetes manages local service lifecycle | Concrete coordination gap |
| Memcached | Deferred | Caching | Invalidation and privacy complexity | Measured repeated-read bottleneck |
| Nginx | Optional | Reverse proxy | Avoid duplicating gateway responsibilities | Actual TLS/edge deployment requirement |
| Akamai | Future external integration | CDN/edge | Requires account, authorization, actual use case | Public edge requirements |
| Atlas | Unresolved product, deferred | Product unspecified | Clarify exact vendor/product and availability before design | Named concrete use case |
| Brave | Not adopted | Alternative tracing instrumentation | Do not duplicate OpenTelemetry tracing | Explicit replacement decision |
| Elasticsearch / Logstash / Kibana | Optional future profile | Log search | Additional memory and operational cost | Core works and log-volume need |

Sources checked:
- [Spring Cloud compatibility](https://spring.io/projects/spring-cloud/)
- [Boot4 system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html)
- [Boot4.0.8 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.0.8/spring-boot-dependencies-4.0.8.pom)
- [Cloud2025.1.3 BOM](https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-dependencies/2025.1.3/spring-cloud-dependencies-2025.1.3.pom)
- [Oracle Free image maintainer](https://github.com/gvenzl/oci-oracle-free)
- [Flyway Oracle support](https://documentation.red-gate.com/flyway/reference/database-driver-reference/oracle-database)
- [Oracle Testcontainers module](https://java.testcontainers.org/modules/databases/oraclefree/)
- [Keycloak downloads](https://www.keycloak.org/downloads)
- [Kafka downloads](https://kafka.apache.org/community/downloads/)

Image tags are verified by registry manifests and pinned to digests in the final deployment lock. Host resources: macOS ARM64; local Docker 11 CPUs, 9 GB. Run Compose and kind serially to avoid exhausting this budget. Each application pool is bounded to 8 connections; five business services must fit Oracle limits (five services × replicas × 8, plus migration/admin connections).

Security override: Tomcat11.0.26, verified against Apache advisories and Maven Central; see ADR007. All Maven plugin versions come from pinned Boot parent. Kind node digest comes from its0.33.0 official release notes. The Java21 runtime image is pinned by digest. Python contract tooling uses `scripts/requirements.lock`; Google Java Format1.28.0 is development-only.

## MVP-2 additions — verified 2026-09-27

- AWS SDK S3 and URLConnection client **2.55.6**, published artifact verified at [Maven Central](https://repo.maven.apache.org/maven2/software/amazon/awssdk/s3/2.55.6/). Uses Java21 and explicit bounded synchronous HTTP, no additional server stack. [AWS HTTP client documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration.html). Compiles with the existing Boot4.0.8 BOM.
- SeaweedFS **4.47**, [official release](https://github.com/seaweedfs/seaweedfs/releases/tag/4.47), ARM64 image pulled and digest pinned. Local single-process S3 with private credentials and persistent volume; only port8333 exposed on loopback. MinIO was considered but its [public repository](https://github.com/minio/minio) is archived; use a maintained alternative. No production HA claim.
- JDK21 ImageIO validates signatures/dimensions and re-encodes JPEG/PNG without original metadata. No third-party image runtime.16 megapixels,8192 per dimension,5MiB input/output; one active decode per media instance and two aggregated upload bodies per gateway instance. Upload quota40/member/hour is recorded in Oracle; concurrency gate is instance-local, not cluster-wide.
- Spring Batch remains deferred: a small bounded durable media worker processes25 records per sweep with row locks and owner-operation fencing. Adopt Batch only for large restartable bulk jobs needing partition/checkpoint tooling. No change to other deferred technologies or the OTel instrumentation approach.

MVP-2 storage permissions: separate admin/application credentials, bucket-scoped Read/Write/List from the [tagged SeaweedFS policy documentation](https://github.com/seaweedfs/seaweedfs/blob/4.47/weed/s3api/policy_engine/README_POLICY_ENGINE.md). Real tests deny application bucket creation and reads outside network-media. Fixed-size S3 uploads explicitly request SHA-256 checksums; streaming-chunk signing caused a live compatibility failure and is not used. [AWS S3 client configuration](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/s3/S3Configuration.html). Download checksum validation remains enabled.

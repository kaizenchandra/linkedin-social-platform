# MVP-1 implementation plan

Scope: the current request and `prompt/mvp-1.md`. Existing prompts/IDE files remain untouched. No previous
implementation exists.

| Phase | Work                                                                             | Required gate                                                              |
|-------|----------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| 0     | Inspect host; verify BOM/artifacts/images; architecture, contracts and decisions | Java 21, Boot 4 + Cloud compatibility, Oracle ARM64 resolved               |
| 1     | Maven, security, migrations, Compose, gateway, idempotent profile                | Compile; Oracle migrations; real JWT request and 401                       |
| 2     | Profiles/search/experience and canonical relationships                           | Oracle constraints, reciprocal requests, ownership                         |
| 3     | Content and current-connection cursor feed                                       | Ownership, unique likes, removal, deterministic paging, dependency failure |
| 4     | Transactional outboxes, relay, atomic notification dedup                         | Replay, broker recovery, concurrent relay, restart                         |
| 5     | Security regression, telemetry, contracts, load, scans                           | Full suite, trace evidence, measured local load                            |
| 6     | Images, kind, migration jobs, persistence and recovery                           | Compose and kind smoke, rolling restart, disposable backup/restore         |

Execute in order. A blocked live gate remains blocked while independent implementation/checks continue. Never treat
generated artifacts as verified deployment.

Assumptions: authenticated public profiles/posts; subject is a UUID from a single Keycloak realm and is the stable
member ID. Maximum 500 accepted connections per member, enforced under ordered member locks; complete IDs fit below
Oracle's 1000-expression legacy IN limit. Feed uses one authenticated internal call, no per-post enrichment. Profile
experiences bounded to 10. Local infrastructure is dedicated and disposable, but volumes are retained unless explicitly
reset.

# MVP-2 progress

**Release 0.2.0: phases 0–6 complete, locally verified on 2026-09-27.**

Implemented avatars and up to four normalized post images with private storage and durable attachment recovery; current MEMBERS/CONNECTIONS visibility and bilateral blocking; participant-only, idempotent one-to-one messaging with monotonic read state; private reporting and audited moderator actions. Six independently deployable applications preserve service-owned data and scoped internal APIs. No next-release work started.

Completed acceptance evidence:

- MVP-1 baseline and regression journeys pass. Final clean Maven build: 32 tests, no failures/errors/skips, real Oracle/S3 Testcontainers.
- Privacy, media validation, interrupted attachment recovery, concurrent messaging, moderation/deletion safety, broker/consumer outages and replay/DLT checks pass against running services.
- Populated MVP-1 migration, Oracle/application restarts, five-schema backup/restore and private-object SHA-256 restore checks pass.
- Fresh Compose and fresh dedicated kind pass the full three-member/moderator journey. Kind also passes two-relay replica checks, all six rolling restarts and server validation of 34 resources.
- Six application image scans have zero HIGH/CRITICAL findings. HTTP/Kafka traces, six scoped metrics targets, four alert rules and the documented short load run pass.

See [verification](mvp2-verification.md) for commands, evidence files, fixed failures and limits. Changed modules: member, content, notification, gateway, narrowly scoped platform-web utilities; added media and messaging. Deployment, contracts, CI, scripts, IntelliJ requests and operational documentation are updated.

Outstanding release blockers: **none**. Remote GitHub Actions is **NOT RUN**. Production HA, sustained capacity, complete erasure, infrastructure-wide vulnerability scanning and identity-provider disaster recovery are not established. Privacy check-to-commit races, retention, local abuse limits and bounded interaction queries are documented.

Environment handover verified: the original Compose dataset is running with all ten core services healthy, optional observability running and six authenticated Prometheus targets up. The verified kind node is stopped to avoid port8080/8180 collision. The temporary fresh-Compose verification project and its newly created volumes were removed; original data and kind PVCs were preserved.

Next executable action: inspect `docker compose ps`, use `python3 scripts/login.py` for interactive PKCE, or open `requests/journey.http` with the generated private `local` IntelliJ environment. Do not begin MVP-3 automatically.

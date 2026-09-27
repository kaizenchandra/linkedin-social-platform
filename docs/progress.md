# MVP-1 progress

Current phase: Phase 6 complete — local deployment and handover verified on 2026-09-27.

All MVP-1 implementation phases passed their local gates. Four independently deployable applications implement profiles, connections, text posts, comments, likes, current-connection feeds and persistent notifications. Oracle migrations, real Keycloak authentication, transactional outboxes and Kafka recovery are exercised against running infrastructure.

Verification: 20 automated tests passed with no failures or skips; Compose and kind acceptance journeys passed; duplicate replay, broker outage recovery, consumer restart, dead-letter repair and two ready relay replicas passed. HTTP/async traces, authenticated metrics, local load, application image scans, rolling restarts and disposable Oracle backup/restore passed. See [verification](verification.md) for commands, results and limits.

Deployment: dedicated `professional-network-mvp` kind cluster, namespace `network-mvp`; seven healthy deployments, four completed jobs and three bound PVCs. Gateway http://localhost:8080 and Keycloak http://localhost:8180. Compose was stopped to keep the local memory budget; its volumes were retained. Nothing was published or deployed externally.

Outstanding blockers: none for the verified local MVP-1 scope. Remote GitHub Actions execution is NOT RUN. Production availability, larger datasets, sustained capacity and identity-provider disaster recovery are outside the evidence. Optional telemetry was verified on Compose, not deployed in kind.

Next executable action: use `python3 scripts/login.py` for interactive PKCE login or inspect the running cluster with `scripts/kubectl-local.sh -n network-mvp get pods,pvc,jobs`. Import root `pom.xml` into IntelliJ with JDK21. Do not begin another release automatically.

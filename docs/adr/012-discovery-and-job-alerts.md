# 012: Explicit discovery and durable job alerts

Accepted2026-09-27. Extend existing owners; no new service, cache, search cluster, language or dependency. Spring Batch evaluated: event-keyed work with small owner transactions and an Oracle cursor needs less machinery than job repository/job-instance orchestration. Use a durable worker with explicit checkpoints/retries/FAILED replay; reconsider Batch for restartable bulk maintenance with larger partitioning needs.

See discovery-and-alert-semantics.md for authorization and epoch boundaries. No private application/message data enters discovery. Profiles, relationships, jobs and preferences remain authoritative in their owner.

```mermaid
sequenceDiagram
 Recruiter->>Hiring: publish draft
 Hiring->>Oracle: job + immutable publication + epoch + outbox
 Oracle-->>Hiring: commit
 Hiring->>Kafka: publication ID (acknowledged relay)
 Kafka->>Hiring: idempotent durable work creation
 Hiring->>Oracle: bounded owner/search batch; match + outbox + checkpoint
 Hiring->>Kafka: generic job alert match ID
 Kafka->>Notification: alert event
 Notification->>Hiring: scoped current match/search/job eligibility
 Notification->>Notification: preference decision + notification + dedup transaction
```

No cross-service atomic cancellation. Current authority is checked at delivery and when a target opens. Retain existing event schema compatibility by adding types and type-specific payload contracts; consumers must be deployed before producers emit new types. Use a maintenance window for the local upgrade.

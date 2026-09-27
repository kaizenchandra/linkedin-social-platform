# 011: Hiring ownership, locking and snapshots

Accepted2026-09-27. One MVC/JPA hiring-service owns the full company/job/application invariant boundary. Existing JDBC
is used for bounded list and audit queries alongside JPA aggregates, following established service patterns. No new
framework/library or shared domain persistence.

Company locks serialize membership and ownership. A deferred composite FK requires the sole owner to be an accepted
member. Membership roles are derived, eliminating competing OWNER rows. Lock order is company, then
job/application/invitation; submission rechecks current membership and job state after obtaining locks. Review versions
detect stale concurrent commands.

Media orchestrates the existing prepare/claim/commit/confirm protocol with hiring as another resource owner; there is no
hiring-to-media synchronous call. Company logos are organization-visible to authenticated members; only the current
owner can attach their own uploaded media. Resolve remains possible after ownership transfer so cleanup never deletes a
valid old owner's logo.

Profile snapshot retrieval uses a dedicated scoped service API and a profile row lock, returning one coherent revision.
PESSIMISTIC_FORCE_INCREMENT on profile writes makes experience-only changes advance the existing version, including
under a fixed clock. Hiring captures the returned version verbatim and stores the job snapshot in its own submission
transaction. Cross-service snapshot time is documented; no distributed transaction.

```mermaid
sequenceDiagram
    participant Applicant
    participant Hiring
    participant Member
    participant Oracle
    participant Kafka
    participant Notifications
    Applicant ->> Hiring: apply(job,key,cover note)
    Hiring ->> Member: scoped snapshot(applicant subject)
    Member -->> Hiring: professional fields + revision
    Hiring ->> Oracle: lock company/job; enforce policy; application + snapshots + outbox
Oracle-->>Hiring: commit
Hiring-->>Applicant: stable application result
Hiring->>Kafka: acknowledged outbox relay
Kafka->>Notifications: application.submitted (IDs only)
Notifications->>Hiring: scoped current company recipients
Hiring-->>Notifications: at most100 member IDs
Notifications->>Notifications: notification fan-out + event dedup in one transaction
```

```mermaid
sequenceDiagram
    participant Owner
    participant Media
    participant Hiring
    Owner ->> Media: attach uploaded logo, company ID, operation ID
    Media ->> Hiring: prepare (current owner check)
    Media ->> Media: claim owned upload
    Media ->> Hiring: commit (recheck current owner)
    Media ->> Media: confirm attachment
    Media ->> Hiring: resolve interrupted claim / authorize download
    Note over Media, Hiring: Hiring never calls media; failed authorization/resolve preserves safety
```

The deferred owner FK has a complete companies (id,owner_id) index. A real transfer/removal test exposed an Oracle
dependency-lock/row-lock cycle without it; the primary-key index on id alone did not cover this composite FK. V5 adds
the index without rewriting applied migrations.
See [Oracle foreign-key locking](https://docs.oracle.com/en/database/oracle/oracle-database/26/cncpt/data-concurrency-and-consistency.html).

Hiring notification envelopes carry actorId plus recipientId (invitation/status) or companyId (submission), never cover
notes, profile snapshots, job text or status previews. Aggregate keys are invitation/application IDs. Generic
notifications do not project mutable state, so delayed events remain generic and current target APIs decide access.
No-op status updates emit nothing. Consumer fan-out commits all recipients and one dedup record atomically;
`(event_id,recipient_id)` is unique. Recipient lookup uses `hiring.recipients` client scope and a company lock. Members
present at lookup receive queued submissions; removed members do not. A membership change after lookup may still leave a
generic identifier notification, but cannot grant application access. A failed lookup retries then reaches the existing
DLT; replay uses the same event ID. At-least-once delivery remains explicit.

Reports require an open, published, unhidden job. Reporter IDs never appear in report DTOs. Moderation
inspection/actions require the signed platform moderator role and an audit reason. Company roles cannot grant it. Hidden
state is separate from lifecycle; restore never changes CLOSED or deadline. Existing application snapshots remain
available under application authorization.

Retention configuration is the versioned `docs/hiring-retention-policy.json` product/operational policy. Null durations
retain records. No automatic purge is implemented or implied; finite retention requires an approved, tested worker and
coordinated backup/replay policy. Withdrawal changes reviewer responses immediately, not stored history or previously
viewed copies.

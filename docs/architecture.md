# Architecture

```mermaid
flowchart LR
 Client -->|PKCE| Keycloak
 Client -->|Bearer JWT| Gateway
 Gateway --> Member
 Gateway --> Content
 Gateway --> Notification
 Gateway --> Media
 Gateway --> Messaging
 Gateway --> Hiring
 Media -->|company logo protocol| Hiring
 Hiring -->|scoped profile snapshot| Member
 Notification -->|scoped current company recipients| Hiring
 Hiring --> H[(Hiring Oracle schema)]
 H -->|outbox relay| Kafka
 Media -->|prepare, commit, resolve, authorize| Member
 Media -->|prepare, commit, resolve, authorize| Content
 Messaging -->|current pair policy| Member
 Media --> O[(Private S3 bucket)]
 Media --> MD[(Media Oracle schema)]
 Messaging --> MS[(Messaging Oracle schema)]
 MS -->|outbox relay| Kafka
 Content -->|client credentials, current connections| Member
 Member --> M[(Member Oracle schema)]
 Content --> C[(Content Oracle schema)]
 Notification --> N[(Notification Oracle schema)]
 M -->|outbox relay| Kafka
 C -->|outbox relay| Kafka
 Kafka --> Notification
```

The Oracle instance and Kafka broker are shared local infrastructure, not high availability. Seven independently packaged applications. `platform-web` holds servlet security/error/pagination mechanics, scoped HTTP client and migration utilities, never persistence entities or business repositories. Gateway uses WebFlux; business services use MVC/JPA.

```mermaid
erDiagram
 MEMBER ||--o{ EXPERIENCE : owns
 MEMBER ||--o{ CONNECTION : participates
 POST ||--o{ COMMENT : contains
 POST ||--o{ POST_LIKE : receives
 NOTIFICATION }o--|| CONSUMED_EVENT : deduplicated
```

Relationships use sorted member IDs with a database unique key. PENDING can become ACCEPTED by recipient, REJECTED by recipient, or CANCELLED by sender. ACCEPTED can become REMOVED by either member. A new request can reopen terminal states. A reciprocal pending request returns conflict, never implicitly accepts. Repeating a completed matching command is idempotent. Ordered participant locks serialize pair creation and degree-limit checks.

Profiles and MEMBERS posts are visible to authenticated unblocked members. CONNECTIONS posts additionally require the author or a current accepted connection. Blocking removes connections and cancels pending requests transactionally. All content paths, images and interactions check current authority; no asynchronous privacy projection. Author deletion is retained separately from moderation hiding, preventing resurrection. Existing generic notifications retain identifiers and can point to an unavailable target.

## MVP-2 privacy, media and messaging

Member remains the authoritative relationship service. Content owns visibility and moderation; media owns validated bytes and attachment lifecycle; messaging owns pair conversations and read positions. Notification payloads stay generic.

```mermaid
sequenceDiagram
 participant Client
 participant Owner as Member or Content
 participant Media
 participant S3
 Client->>Media: bounded upload with user JWT
 Media->>Media: validate and normalize bytes; durable metadata
 Media->>S3: private random object key
 Client->>Media: attach owned media IDs and operationId
 Media->>Owner: prepare durable operation intent
 Media->>Media: atomically claim owned media IDs
 Media->>Owner: commit references (only if still PREPARED)
 Media->>Media: confirm ATTACHED (retryable)
 Media->>Owner: reconcile expired claims via local operation status
 Note over Owner,Media: Status endpoint never calls media
 Client->>Media: download with user JWT
 Media->>Owner: authorize current attachment and viewer
 Owner-->>Media: allow or deny (current member policy)
 Media->>S3: stream private object only if allowed
```

```mermaid
sequenceDiagram
 participant Client
 participant Messaging
 participant Member
 participant Oracle
 participant Kafka
 Client->>Messaging: send(conversation,clientId,text)
 Messaging->>Member: current connected/unblocked policy
 Messaging->>Oracle: lock pair; dedup; sequence; message + outbox
 Oracle-->>Messaging: commit
 Messaging-->>Client: stable message result
 Messaging->>Kafka: relay generic message event after commit
```

## MVP-3 hiring extension

Hiring owns one Oracle schema for the company/job/application transactional boundary. Organization membership is current database state, independent of personal blocking. See ADR011 and the hiring authorization matrix for privacy and lock ordering.

```mermaid
flowchart LR
 Gateway --> Hiring
 Hiring -->|scoped professional snapshot| Member
 Media -->|company attachment and download authorization| Hiring
 Hiring --> H[(Hiring schema: companies, memberships, jobs, applications, audits, outbox)]
 H -->|outbox relay| Kafka
 Kafka --> Notification
 Notification -->|scoped current recipients| Hiring
```

```mermaid
erDiagram
 COMPANY ||--|{ COMPANY_MEMBER : authorizes
 COMPANY ||--o{ INVITATION : issues
 COMPANY ||--o{ JOB : owns
 JOB ||--o{ APPLICATION : receives
 APPLICATION ||--|{ APPLICATION_HISTORY : records
 JOB ||--o{ JOB_REPORT : receives
 COMPANY ||--o{ HIRING_AUDIT : records
```

An owner_id pointer references exactly one accepted company member; transfer changes that pointer atomically and leaves the previous owner as recruiter. Application snapshots preserve the fetched member version and locked job version. Reports and hidden state do not change job lifecycle.

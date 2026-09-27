# Discovery and alert semantics

| Relationship/state        | Feed eligibility                                       | Other permissions                            |
|---------------------------|--------------------------------------------------------|----------------------------------------------|
| Self                      | Own visible nondeleted posts                           | Existing ownership                           |
| Accepted connection       | MEMBERS and CONNECTIONS                                | Existing connection policy                   |
| Follow only               | MEMBERS only                                           | No messaging or connection privilege         |
| Connected and followed    | One post occurrence                                    | Connection permission remains independent    |
| Block in either direction | Excluded; both follows removed                         | Existing personal privacy applies            |
| Company follow            | Optional interactive job filter                        | No recruiter/owner access; no implicit alert |
| Saved post                | Current authorization required on every read           | No privilege or retained preview             |
| Closed saved job          | Current title/company/location and CLOSED summary only | No application; hidden jobs omitted          |

Feed candidates are bounded and hydrated/authorized in batches; cursors advance over examined candidates even when all
are rejected. A page may be empty with a next cursor. Relationships and visibility can change between pages; no snapshot
consistency is promised. Dependency failure returns503, never an unverified partial feed. Member suggestions use only
mutual accepted connections with bounded candidate exploration, excluding self/current connections/follows/pending
pairs/blocks. Reasons expose no mutual identities/counts. Company suggestions use explicit filters only and exclude
follows; no inference from applications or messages.

Saved-search criteria share literal keyword/location and structured matching with interactive job search. Explicit
company IDs only; followed-company selections must be resolved by the client. Name-only edits preserve criteria version.
Criteria changes increment version; enabling is a new prospective activation. Deletion is a retained tombstone for
durable match references.

Alert boundary: a service-owned monotonic eligibility epoch serializes publication and saved-search criteria/activation
changes within their Oracle transactions. The commit that acquires the epoch row first precedes the other; timestamps
remain UTC metadata, not the sole race arbiter. A current search must have criteria and activation epochs no later than
publication. Updating criteria or disabling/re-enabling excludes unfinished old publications. No historical backfill.

Publication transitions atomically store immutable matching fields/snapshot plus an outbox event referencing that
snapshot. Only DRAFT->PUBLISHED emits it. Kafka ingestion creates one durable work item and returns; it never scans
searches. A small durable worker checkpoints by owner ID, processing one owner's at-most10 searches per transaction,
with a bounded per-tick budget, retry/backoff and FAILED replay. An owner lock serializes search commands and match
commits. Unique (member,job) match chooses the smallest eligible matching search ID deterministically; match and
notification outbox commit together. Existing matches never produce a second alert after criteria changes or replay.

Notification delivery checks current match/search version/enabled state and current job publication/deadline/moderation
through a scoped hiring API. Preferences are notification-owned; absent override permits only explicitly enabled
eligible searches. Global disable affects job alerts only. Failed eligibility lookup retries/DLT; it never assumes
eligibility. A successful eligibility/preference decision is the boundary for delivery already in progress: later
cross-service changes cannot atomically recall that notification. Delivered generic IDs are retained; opening the job
rechecks current authorization. No stale descriptions/previews or exactly-once claim.

Implementation bounds: feed and saved posts scan100 candidates per batch, maximum500 per page; author IDs partitioned
into500-entry SQL IN clauses (up to1001 total), policy batches contain at most100 distinct authors. Hydration uses one
post query and one media-reference query per page. Saved jobs scan at most500 rows. Empty pages may carry a nextCursor;
clients must follow it. Current relationship authorization is sampled per batch; no cross-service atomicity with a
concurrent block is claimed.

Member suggestions explore the first5000 accepted edges ordered by canonical pair and mutual member ID, then rank
eligible candidates by distinct mutual connections descending, member ID ascending. This can omit candidates outside the
bounded exploration; exact counts and mutual identities are never returned. Company filters are trimmed,
case-insensitive exact industry/location matches, ordered by company ID; absent filters produce a directory suggestion
with that explicit reason. One SQL query per suggestion endpoint; no cache and no remote hydration.

Matching scheduling: at most25 one-owner transactions per tick, default1-second fixed delay. Work selection orders by
its last progress timestamp, so multiple publications share the budget; one pending publication can use all25 steps.
Each owner has at most10 searches. Outbox backlog of1000 pauses new matching (existing committed outbox continues
relaying). A failure rolls back the whole step; a separate transaction stores retry state, exponential2/4/8/16-second
delays and FAILED after five attempts. Failed replay preserves the last committed owner cursor. Semantic job/member
uniqueness fences replay even with a different event ID. No-op publish/edit/restore never creates another publication.

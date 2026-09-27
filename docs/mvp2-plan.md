# MVP-2 implementation plan

Scope is prompt/mvp-2.md. Preserve MVP-1 APIs and stack; add only media-service and messaging-service. No external
deployment. Work through gates in order.

| Phase | Acceptance gate                                                                                                                               |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| 0     | Real MVP-1 tests and smoke; privacy/state models; additive migration strategy                                                                 |
| 1     | Blocks serialize with connections; existing posts MEMBERS; all content paths enforce current policy; generic notifications contain no content |
| 2     | Private validated images; durable attachment states; owner authorization; safe reconciliation/cleanup and outage checks                       |
| 3     | Unique pairs; idempotent ordered messages; monotonic reads; participant isolation; generic notifications                                      |
| 4     | Private reports; trusted moderator role; audited inspection/actions; hidden/deleted separation                                                |
| 5     | Oracle/Kafka/S3 failure paths, privacy regression, contracts, bounded load, telemetry                                                         |
| 6     | Populated upgrade, fresh Compose, local kind, restart persistence, CI and handover                                                            |

Migration strategy: never modify applied MVP-1 migrations. Add visibility with MEMBERS default; new block, media and
messaging tables with database uniqueness; retain author-deletion independently of moderation state. Test both populated
upgrades and fresh schemas. Rolling back to MVP-1 would bypass new privacy controls and is unsupported after private
content exists; use forward fixes or restore a coordinated pre-upgrade backup.

State models: block/unblock are directed idempotent commands under ordered member locks; either direction denies new
interaction and block terminates pending/accepted connections. Messaging locks the unique canonical conversation,
increments a per-conversation sequence and deduplicates (conversation,sender,clientId). Read positions advance by max
(old,requested), only to a message in that conversation.

Media state model: TEMPORARY -> READY -> CLAIMED -> ATTACHED -> DELETE_PENDING -> deleted. Authoritative resources
durably record attachment operations before claims; cleanup cannot race a new claim because both lock media metadata.
Claims are reconciled using a resource-owner operation status, never inferred from an unavailable dependency. Images are
normalized with JDK ImageIO after signature and dimension validation. Bounds: 5MiB input, 16 megapixels, maximum8192
pixels per dimension, four post images, one avatar. Streams are authorized on each request, no public URLs. Durable
small worker preferred over Spring Batch for bounded per-object transitions; reassess Batch for large restartable bulk
jobs.

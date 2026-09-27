# Roadmap

MVP-1 and MVP-2 are implemented and locally verified. MVP-2 adds avatars/post images, visibility/blocking, one-to-one text messages and reporting/moderation. No later release is implemented or authorized by this work.

Future releases follow AGENTS.md: companies/jobs/applications (MVP-3), following/saved items/discovery (MVP-4), live updates/replay (MVP-5), account lifecycle/export/deletion (MVP-6), further operational hardening (MVP-7), and skills/recommendations (MVP-8).

REST polling remains authoritative. A future WebSocket layer can deliver hints and durable cursors without replacing message/read state. Adoption needs replay, reconnect, bounded buffers and token-expiry design; it is not part of MVP-2.

Messages, reports/audits, notifications/dedup records and author-deletion tombstones currently have no automatic expiry. Media cleanup removes safely unreferenced objects; backups can retain copies. Account deletion, retention enforcement and complete erasure require coordinated lifecycle work across service-owned data, events and backups. Do not promise regulatory compliance or production availability from the current local evidence.

# Roadmap

MVP-1 and MVP-2 are implemented and locally verified. MVP-3 hiring is implemented and locally verified; evidence is in mvp3-verification.md. MVP-2 adds avatars/post images, visibility/blocking, one-to-one text messages and reporting/moderation. MVP-4 following, saved items, rule-based discovery and job alerts are implemented and locally verified on Compose and kind; evidence is recorded in mvp4-verification.md. MVP-5 live updates, replay, read synchronization and private controls are implemented and locally verified; see mvp5-verification.md. No release after MVP-5 is authorized by this work.

Future releases follow AGENTS.md: account lifecycle/export/deletion (MVP-6), further operational hardening (MVP-7), and skills/recommendations (MVP-8).

REST remains authoritative; MVP-5 uses bounded SSE invalidations and durable owner cursors. WebSockets remain deferred until a measured bidirectional transport requirement justifies their added lifecycle complexity.

Messages, reports/audits, notifications/dedup records and author-deletion tombstones currently have no automatic expiry. Media cleanup removes safely unreferenced objects; backups can retain copies. Account deletion, retention enforcement and complete erasure require coordinated lifecycle work across service-owned data, events and backups. Do not promise regulatory compliance or production availability from the current local evidence.

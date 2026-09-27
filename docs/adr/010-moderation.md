# 010: Reports and audited moderation

Accepted 2026-09-27. Content-service owns reports, audit and hidden/deleted state. A functional Oracle unique index
enforces one OPEN report per reporter/type/target. Report responses omit reporter identity; own-report queries are
subject-scoped. Queue and inspection are separate: text is returned only by the explicit inspection command, which
records the moderator, target, reason and timestamp transactionally.

The signed Keycloak realm_access.roles claim supplies moderator authority. Roles are assigned through identity-provider
administration, never profile/request data. Both HTTP rules and method authorization protect moderation operations.
Moderators get no messaging privilege. Every inspect/hide/restore/dismiss records a bounded reason. Only a changed
hidden flag emits a generic outbox notification.

Author deletion records deleted_at independently of hidden. Normal reads/listings/counts/interactions require neither
hidden nor deleted; comment visibility also depends on the parent. Restoring only clears hidden and rejects
author-deleted targets/parents. Author-deleted bodies are redacted in moderator inspection. Audit/report records remain
retained, and database backups may retain personal content; complete erasure is not claimed. MVP-1 physical deletion
behavior changes to retained tombstones for moderation consistency; deletion/export/retention jobs remain a later
lifecycle release.

# MVP-3 plan

Scope: hiring-service owns companies, accepted memberships, invitations, jobs, applications/snapshots, reports/audits and its outbox. Preserve existing APIs and stack. Release artifacts will be0.3.0; no framework upgrade or new dependency.

| Phase | Acceptance gate |
|---|---|
| 0 | Existing32 tests and both release journeys; matrices/state models/additive migrations |
| 1 | Company isolation, owner-only membership/logo changes, atomic transfer, invitation races, revoked access |
| 2 | Draft/publish/close, validated salary/deadlines, bounded cursor search, real indexed structured filters |
| 3 | Applicant-scoped idempotency, stable snapshots, close/submit and review races, withdrawal redaction |
| 4 | Current-recipient notification routing, retry/replay, audited moderation, hidden/deadline/lifecycle invariants |
| 5 | Real Oracle/Kafka/privacy failures, contracts, bounded search/submission/recruiter load, telemetry |
| 6 | Fresh Compose/kind, populated MVP-2 migration, restart/backup, CI and handover |

Company: exactly one non-null owner_id referencing an accepted membership through a deferred composite FK. Roles derive from that pointer (owner) or accepted membership (recruiter), never JWT organization claims. All membership changes lock the company. Previous owner becomes recruiter after transfer. Limit100 accepted members/company bounds event recipient resolution; invitations expire after7 days. No company deletion or verification claim.

Jobs: DRAFT -> PUBLISHED -> CLOSED; repeated publish/close are no-ops in their completed state, reopening forbidden. Visibility is a separate hidden flag. Deadline is enforced at submission/search/read time in UTC. Search is case-insensitive literal substring over title/description and location, with escaped SQL wildcards, structured equality filters and publishedAt/id cursor; no total counts or full-text indexing claim.

Applications: SUBMITTED -> IN_REVIEW or SHORTLISTED or REJECTED; IN_REVIEW -> SHORTLISTED or REJECTED; SHORTLISTED -> REJECTED. Applicant may withdraw any nonterminal state. Terminal states cannot reopen. Review commands carry expectedVersion; identical no-op state updates emit no event. Company then job/application locks serialize membership, closing and submission. An application either commits before close or returns409 after close.

Submission first checks an existing applicant/key, fetches an authorized versioned profile snapshot, then locks company/job and rechecks key/current policy. One application per applicant/job and applicant/key constraints resolve races. Changed key input conflicts. Profile edits do not update snapshots; job snapshot captured under the job lock. Member outage returns503, never incomplete data.

Migrations are additive: new hiring schema, company media type, coherent profile revision locking, notification(event,recipient) uniqueness for fan-out. Test empty schemas and populated MVP-2 upgrade. Do not roll back notification/media binaries after new event/resource types exist; use forward fixes or coordinated pre-upgrade restore.

Retention is a configurable operational product policy (default indefinite pending policy approval); no automatic purge or legal-compliance claim. Withdrawal preserves applicant history but redacts recruiter-facing cover/profile immediately. Previously read data/backups cannot be revoked. Spring Batch remains deferred until a real approved restartable retention workflow exists.

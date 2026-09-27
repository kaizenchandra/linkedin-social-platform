Paste this into the same IntelliJ Codex project. MVP-8 adds **professional skills, peer endorsements, and written recommendations**, with consent, moderation, and lifecycle controls built on the existing backend.

You are my principal backend engineer and implementation agent working inside IntelliJ.

Extend the existing professional networking platform from MVP-7 to MVP-8 through incremental, verified development.

Implement actual code, migrations, tests, API contracts, deployment updates, and documentation. Do not stop after planning or scaffolding.

## 1. Starting point and execution rules

The intended platform uses:
- Java 21, Spring Boot 4.x, Spring Cloud, Spring Security, and Maven.
- Gateway, member, content, media, messaging, notification, and hiring services.
- Keycloak for OIDC.
- Oracle with service-owned schemas.
- Kafka with transactional outbox publishing and idempotent consumers.
- Private S3-compatible storage.
- Docker Compose and Kubernetes.
- Metrics, logs, distributed tracing, and release verification.

Previous releases should provide:
- Profiles, connections, follows, publishing, messaging, and discovery.
- Companies, jobs, applications, and job alerts.
- Privacy, blocking, moderation, and account lifecycle controls.
- Data export/deletion, live updates, and operational hardening.

Treat these as intended capabilities, not verified implementation.

Before editing:
- Read applicable AGENTS.md instructions.
- Inspect actual code, contracts, migrations, ADRs, and verification records.
- Run available baseline checks.
- Identify missing prerequisites.
- Repair prerequisites required by MVP-8.
- Preserve unrelated work.

Continue automatically after phase verification gates pass.

Do not:
- Rewrite working services unnecessarily.
- Perform unrelated dependency upgrades.
- Add unused technologies.
- Disable security or weaken tests.
- Claim unexecuted checks passed.
- Deploy to shared or paid infrastructure without authorization.

## 2. MVP-8 objective

Add a professional credibility layer:

1. Members list skills on their profiles.
2. Accepted connections endorse listed skills.
3. Members request written recommendations from connections.
4. Recommendation authors and recipients control publication.
5. Moderators handle reported professional-profile content.
6. Existing export, deletion, discovery, and notifications include these features safely.

Keep the scope limited.

Exclude:
- Verified credentials or certifications.
- Skill assessments and exams.
- Automated proficiency scores.
- AI-generated recommendations.
- Candidate ranking based on endorsements.
- Paid endorsements or recommendations.
- External credential-provider integrations.
- Anonymous endorsements.
- Rich-text formatting and attachments.
- Frontend development.

A listed skill or peer endorsement is a member assertion, not verified proficiency. Do not present it as verified expertise.

## 3. Architecture and ownership

Keep these features inside `member-service`:
- Skill catalog.
- Profile skills.
- Endorsements.
- Recommendation requests.
- Recommendation revisions and publication.
- Reports and moderation for recommendations.

Do not create skill, endorsement, reputation, or recommendation microservices.

Existing services retain their responsibilities:
- Notification-service handles new notification types.
- Hiring-service obtains application profile snapshots through existing contracts.
- Gateway handles routing and existing edge controls.

Rules:
- No cross-service database access.
- No shared persistence entities.
- Use existing authorization and lifecycle mechanisms.
- Use transactional outbox events for notifications.
- Keep connection, blocking, and new member-domain invariants transactional where they share member-service.

Document significant decisions with concise ADRs.

## 4. Skill catalog

Create a small, curated skill catalog.

Fields:
- Stable identifier.
- Display name.
- Normalized lookup name.
- Active/inactive status.

Seed an original, modest catalog covering common professional skills. Do not scrape proprietary taxonomies.

Support:
- Search active skills by bounded prefix or substring matching.
- Paginated results.
- Stable ordering.
- Idempotent seed migrations.

Catalog administration:
- Use an explicitly authorized platform role.
- Permit adding, renaming, and deactivating skills.
- Prevent normalized duplicates.
- Audit changes.
- Do not let ordinary members create arbitrary catalog entries in MVP-8.

Deactivation:
- Prevent new selection of the skill.
- Preserve existing profile references and historical snapshots.
- Return its inactive state clearly.
- Do not silently rewrite or merge skill identities.

Alias management and catalog merging are deferred.

## 5. Profile skills

Allow members to:
- Add catalog skills to their profile.
- Remove skills.
- Reorder displayed skills.
- Select up to three featured skills.

Default limit: 30 skills per member.

Rules:
- Only the profile owner can modify the list.
- Prevent duplicates with database constraints.
- Enforce limits under concurrent requests.
- Validate that featured skills belong to the member’s current list.
- Make repeated add/remove commands idempotent.
- Use optimistic concurrency or equivalent protection for reorder operations.

Do not ask members to provide numeric proficiency scores in this release.

Removing a skill:
- Removes it from the public profile.
- Invalidates endorsements attached to that profile-skill association.
- Re-adding the catalog skill creates a new association.
- Old endorsements must not silently reappear.

Return profile skills through compatible API additions. Preserve existing profile clients.

## 6. Skill endorsements

Allow an authenticated member to endorse a skill currently listed by an accepted connection.

Support:
- Endorse a profile skill.
- Withdraw my endorsement.
- View a bounded list of endorsers where authorization permits.

Rules:
- No self-endorsement.
- Both members must be active.
- The pair must be connected and unblocked when creating an endorsement.
- Enforce one endorsement per endorser and profile-skill association.
- Repeated endorsement requests are idempotent.
- Repeated withdrawal requests are idempotent.

An endorsement is a peer statement, not proof of competence.

After connection removal:
- Existing endorsements remain.
- New endorsements are prohibited until the pair reconnects.
- Either endorser may still withdraw their own endorsement.

Blocking:
- Withdraw endorsements between the pair in both directions.
- Unblocking does not restore them automatically.

Deactivation:
- Hide endorsements when the endorser or recipient is deactivated.
- Reactivation may restore eligible endorsements unless they were withdrawn or otherwise invalidated.

Deletion:
- Remove endorsements authored by or attached to the deleted member.
- Delayed events must not recreate them.

Visibility:
- Respect profile access, lifecycle, and viewer blocking rules.
- Counts must not reveal endorsers hidden from the viewer.
- Paginated endorser results and counts must use the same eligibility policy.
- Do not expose private account information.

Do not calculate a global reputation score from endorsement totals.

## 7. Recommendation requests

Allow a member to request a written recommendation from an accepted connection.

A request contains:
- Requester/recipient.
- Requested author.
- A bounded optional request message.
- Creation and expiry timestamps.
- Status.

Support:
- Create.
- List incoming and outgoing requests.
- Decline.
- Cancel.
- View status.

Default expiry: 30 days.

Rules:
- Prevent self-requests.
- Require active, connected, unblocked members.
- Allow only one open request per author/recipient pair.
- Enforce limits and cooldowns using the existing abuse-control framework.
- Keep request messages private to the two participants.
- Expired, declined, or cancelled requests cannot be used to submit new recommendation text.

Requests are optional invitations. Declining must not create a public signal.

Use an injectable clock for expiry tests.

## 8. Written recommendations and publication consent

A requested author can:
- Create a private draft.
- Edit the draft.
- Submit it to the recipient.
- Withdraw an unpublished submission.
- Revoke a published recommendation.

The recipient can:
- Approve a submitted revision for publication.
- Decline it.
- Hide a published recommendation from their profile.

Neither participant can edit the other participant’s contribution.

Use plain text with a maximum length of 3,000 characters.

Versioning:
- Store submitted recommendation revisions immutably.
- Approval refers to an exact revision.
- Editing after submission creates a new revision.
- A previously approved revision remains published while a replacement awaits approval.
- Publishing a replacement atomically changes the approved revision.
- Rejected or withdrawn drafts never replace the published text.

Do not implement publication as one mutable text field with a boolean flag.

Before submission or approval:
- Recheck connection, blocking, and lifecycle state.
- Bind commands to the expected version.
- Reject stale or conflicting operations predictably.

Author revocation:
- Immediately removes normal visibility.
- The recipient cannot republish the revoked revision.

Recipient hiding:
- Removes normal visibility.
- The recipient may restore the same approved revision only if it remains eligible and the author has not revoked it.

Historical connection removal:
- Does not automatically unpublish an already approved recommendation.
- Prevents new submissions and approvals until reconnection.

## 9. Blocking and lifecycle behavior

Blocking between author and recipient:
- Cancels open requests.
- Prevents new drafting, submission, and approval.
- Removes existing recommendations between the pair from normal visibility.
- Invalidates previous publication approval.
- Unblocking does not automatically restore publication.

Deactivation:
- Temporarily hides recommendations involving the deactivated member.
- Preserve independent revocation, moderation, and approval states.
- Reactivation restores only otherwise eligible publications.

Deletion:
- Delete private request messages and unpublished drafts involving the deleted member.
- Remove published recommendation text authored by or written about the deleted member.
- Retain only justified minimal audit/tombstone records.
- Do not retain recommendation text under a generic audit exception.
- Prevent stale events from recreating deleted records.

Apply the existing lifecycle write fence and generation checks.

Do not invent a new deletion mechanism separate from MVP-6.

## 10. Recommendation moderation

Allow authenticated viewers to report a currently visible recommendation.

Reasons:
- Spam.
- Harassment.
- Misleading content.
- Other, with a bounded explanation.

Implement:
- One active report per reporter/recommendation.
- Moderator-only queue.
- Dismissal.
- Hide and restore actions.
- Audited reasons and timestamps.

Moderation is separate from publication consent.

Restoring moderation status must not override:
- Author revocation.
- Recipient hiding.
- Invalidated approval.
- Blocking.
- Deactivation.
- Deletion.

Reporters cannot view other reports. Authors and recipients cannot discover reporter identities.

Moderators may inspect reported recommendation content through audited endpoints. This does not grant access to unrelated private request messages or drafts.

Do not label moderation decisions as verified assessments of professional ability.

## 11. Notifications

Add notifications for:
- A recommendation request.
- A recommendation submitted for review.
- Approval of a recommendation.

Do not add one notification per endorsement by default; avoid unnecessary notification volume.

Notification rules:
- No duplicate notification for an idempotent retry.
- No self-notifications.
- No private recommendation text or request message in event payloads.
- Current authorization is checked when the target is opened.
- Expired or cancelled targets are handled safely.
- Account lifecycle restrictions remain enforced.

Use existing outbox publishing, consumer deduplication, preference checks, and live notification delivery.

Do not modify stream ordering or replay semantics unnecessarily.

## 12. Discovery, hiring, and exports

Member search:
- Add an optional exact skill-ID filter.
- Match the member’s currently listed skills.
- Preserve existing visibility and blocking rules.
- Do not rank results by endorsement count.

Hiring:
- Add listed skills to new application-time profile snapshots.
- Preserve old snapshots unchanged.
- Do not embed live endorsement counts or recommendations into applications.
- Do not silently change recruiter ranking.

Export:
- Include the member’s listed skills.
- Include their authored endorsements.
- Include recommendation requests, authored revisions, and received recommendations they are authorized to access.
- Exclude reporter identities and private moderator notes.
- Update the field-level export policy.

Deletion:
- Extend existing service cleanup and verification.
- Verify new tables, notifications, replay records, and delayed events follow the lifecycle policy.

## 13. API and persistence requirements

Provide:
- Versioned REST contracts.
- Explicit DTOs.
- Bounded validation and pagination.
- Consistent Problem Details errors.
- Stable identifiers and UTC timestamps.
- Compatible profile API additions.
- Database constraints for uniqueness and limits where applicable.
- Appropriate indexes.
- Optimistic locking for revision-sensitive commands.

Test migrations:
- From an empty database.
- From populated MVP-7 data.

Avoid destructive changes to existing profile and application tables.

Create a clear transition table for:
- Recommendation requests.
- Submitted revisions.
- Publication consent.
- Author revocation.
- Moderation.

Keep these states explicit rather than encoding every combination in one oversized status enumeration.

## 14. Development phases

### Phase 0 — Baseline and design

Deliver:
- MVP-7 verification results.
- MVP-8 acceptance checklist.
- Domain model.
- Authorization and visibility matrix.
- State-transition tables.
- Export/deletion changes.
- API and event contracts.

Gate:
- Resolve prerequisites.
- Define publication, blocking, and revision semantics before implementation.

### Phase 1 — Skills

Implement catalog administration, profile skills, ordering, and featured skills.

Gate:
- Duplicate and concurrent additions respect limits.
- Catalog deactivation preserves historical references.
- Existing profile clients remain compatible.

### Phase 2 — Endorsements

Implement endorsements, withdrawal, eligible counts, and lifecycle behavior.

Gate:
- Only eligible connections can endorse.
- Concurrent retries create one endorsement.
- Removing and re-adding a skill does not restore old endorsements.
- Viewer privacy rules apply to both counts and lists.

### Phase 3 — Recommendations

Implement requests, drafts, immutable submissions, approval, hiding, and revocation.

Gate:
- Approval always identifies an exact revision.
- Unapproved edits never change published text.
- Concurrent approve/edit/revoke operations behave predictably.
- Blocking and lifecycle restrictions are enforced.

### Phase 4 — Moderation and integration

Implement reports, moderation, notifications, skill search, application snapshot additions, and export/deletion integration.

Gate:
- Moderation restoration cannot override consent or lifecycle restrictions.
- Notifications are deduplicated.
- Existing application snapshots remain unchanged.
- Exports and deletion cover the new data.

### Phase 5 — Hardening and release

Run security, concurrency, integration, load, and regression tests.

Update deployment configuration, CI, runbooks, and IntelliJ HTTP examples.

Gate:
- Fresh setup works.
- Upgrade from populated MVP-7 data works.
- Existing release checks remain intact.
- End-to-end tests pass against the deployed local environment.
- Unexecuted checks are explicitly reported.

## 15. End-to-end acceptance scenario

Automate:

1. Create connected members A and B and an unrelated member C.
2. Add and feature skills on A’s profile.
3. Endorse A’s skill as B.
4. Retry concurrently and verify one endorsement.
5. Verify C cannot endorse without connecting.
6. Remove and re-add the skill; verify the endorsement does not return.
7. Request a recommendation from B as A.
8. Submit a revision as B.
9. Approve it as A and verify publication.
10. Submit an edited revision and verify the approved text remains unchanged.
11. Approve the replacement and verify atomic publication.
12. Revoke it as B and verify A cannot restore it.
13. Exercise blocking and verify approvals are not automatically restored after unblocking.
14. Report another visible recommendation.
15. Hide it through moderation and verify all ordinary read paths.
16. Verify moderator restoration cannot override author revocation.
17. Submit a job application and verify its skill snapshot remains stable after profile changes.
18. Export and then delete a disposable account.
19. Replay old events and verify deleted data is not recreated.
20. Run MVP-1 through MVP-7 regression suites.

Use real Oracle and Kafka integration tests where applicable.

Mocks must not be the only evidence for concurrency, persistence, notification delivery, or lifecycle correctness.

## 16. Progress and definition of done

Maintain:
- `docs/mvp8-plan.md`
- `docs/mvp8-progress.md`
- `docs/mvp8-verification.md`
- `docs/professional-credibility-policy.md`

Update existing ADRs, contracts, lifecycle policies, runbooks, and release gates.

At each phase boundary:
- Summarize implemented behavior.
- Record executed checks.
- Separate PASS, FAIL, BLOCKED, and NOT RUN.
- Record unresolved issues and the next executable action.

If interrupted, save a precise checkpoint and resume from repository evidence.

MVP-8 is complete when:
- Skills, endorsements, and recommendations work through real APIs.
- Publication requires explicit consent to an immutable revision.
- Blocking, moderation, revocation, and lifecycle rules remain enforceable.
- New data participates in export and deletion.
- Existing application snapshots and APIs remain compatible.
- Populated MVP-7 data upgrades successfully.
- Required tests and deployment checks are supported by evidence.

Do not claim verified expertise, credential validation, unbiased hiring, or production readiness beyond the implemented and tested behavior.

Start by inspecting MVP-7 and running baseline verification. Then implement MVP-8 phase by phase until the acceptance criteria are satisfied.
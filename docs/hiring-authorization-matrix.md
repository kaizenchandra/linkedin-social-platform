# Hiring authorization and privacy

| Action                                     | Ordinary member/applicant                                                     | Current recruiter                     | Current owner               | Platform moderator                            |
|--------------------------------------------|-------------------------------------------------------------------------------|---------------------------------------|-----------------------------|-----------------------------------------------|
| Read company/logo                          | Authenticated allow                                                           | Allow                                 | Allow                       | Same public access                            |
| Edit company/logo; invite/remove; transfer | Deny                                                                          | Deny                                  | Own company only            | No bypass                                     |
| Accept/reject invitation                   | Invited subject only                                                          | Same                                  | Same                        | No bypass                                     |
| Read draft/manage jobs                     | Deny                                                                          | Own company                           | Own company                 | Explicit audited reported-job inspection only |
| Search/read published unhidden job         | Allow before deadline                                                         | Own jobs through management           | Same                        | Same public policy                            |
| Apply                                      | Initialized profile; published, unhidden, before deadline; not company member | Cannot apply to own company           | Cannot apply to own company | No bypass                                     |
| Read application/history                   | Applicant's own only                                                          | Own company, current membership       | Same                        | No bypass                                     |
| Change application status                  | Withdraw own active application                                               | Own company; legal transition/version | Same                        | No bypass                                     |
| Read withdrawn cover/profile               | Applicant only                                                                | Redacted                              | Redacted                    | No bypass                                     |
| Report published job                       | Current public access only                                                    | Same                                  | Same                        | Same                                          |
| Queue/inspect/hide/restore/dismiss         | Deny                                                                          | Deny                                  | Deny                        | Trusted IdP role; reason and audit            |

Company roles are authoritative database state on every request, not token claims. Cross-company/guessed application
identifiers return404. Unauthorized company management also returns404; valid state conflicts409; dependency failure503.
No candidate counts on public job APIs. Company pages are explicitly UNVERIFIED.

Personal blocking does not hide submitted applications from current authorized company reviewers. Application submission
is a separate disclosure to that company. Recruiting access cannot bypass personal-profile or messaging authorization.
Company logos represent organizations, not personal avatars, and remain member-visible after personal blocking.

Generic notifications contain identifiers/type only. Opening a target requires its current API authorization.
New-application recipients are resolved through a scoped hiring internal API at consumer processing time: removed
reviewers are excluded, newly accepted reviewers may receive an older event. Recipient lookup failure retries/DLT; it is
never treated as an empty successful recipient list. Membership can change after lookup; no cross-service atomicity is
claimed, and notification contents disclose no candidate data.

Snapshots contain display name, headline, summary, location and professional experience plus member ID/version; no
email, credentials, connection lists, image URLs or messages. Profile snapshots are immutable at submission. Withdrawal
redacts cover/profile from reviewer responses, while minimal status/job history remains; retention is separate from
visibility and does not promise immediate erasure.

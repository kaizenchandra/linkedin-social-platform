# MVP-2 privacy policy

Current authoritative member relationships are checked synchronously; never cached or inferred from Kafka. Denied
resource access returns404, authorization dependency outage503. Moderator role has no authority over messaging.

| Resource/action                        | Self/author                                | Accepted unblocked peer | Unrelated unblocked member                | Either-direction block      |
|----------------------------------------|--------------------------------------------|-------------------------|-------------------------------------------|-----------------------------|
| Profile/search/avatar                  | Allow                                      | Allow                   | Allow                                     | Omit/404                    |
| MEMBERS post/comments/likes/images     | Allow                                      | Allow                   | Allow                                     | Omit/404                    |
| CONNECTIONS post/comments/likes/images | Allow                                      | Allow                   | Omit/404                                  | Omit/404                    |
| New connection                         | Reject self                                | Existing idempotent     | Allow                                     | 404                         |
| Start conversation/new message         | Reject self-pair                           | Allow                   | Reject                                    | Reject                      |
| Existing message history/read position | Participants only                          | Participants only       | Participants retain history after removal | Participants retain history |
| Report visible post/comment            | Allow                                      | Follow target access    | Follow target access                      | Deny                        |
| Hidden or author-deleted content       | No normal read                             | No normal read          | No normal read                            | No normal read              |
| Moderator inspect/hide/restore         | Explicit moderator endpoint and audit only | Same                    | Same                                      | Same                        |

Blocking removes accepted connections and cancels requests in the same member transaction. Unblocking does not restore
them. Current post state controls comments, interactions and attached media; moderation does not change the underlying
MEMBERS/CONNECTIONS setting. Durable notifications carry only generic event type and identifiers, never message/post
text, profile names or media URLs; clients resolve targets through their authoritative API and display an unavailable
target on404. There is no embedded target preview endpoint.

List authorization uses bounded batch policy lookups, not one request per item. Pagination can omit inaccessible items
and must never expose their content. A successful synchronous policy check can race a later block/disconnection before a
transaction in another service commits; there is no cross-service atomicity. Future requests re-evaluate current policy.
Streams authorized before a policy change may complete; bytes already downloaded cannot be revoked.

Moderation state is independent of author deletion: hidden content is excluded from normal content queries/counts and
media authorization even for its author. Authors may remove their own hidden content. Restore only clears the moderation
flag and must reject an author-deleted target or a comment whose parent is author-deleted. Explicit moderator inspection
writes an audit record and is the only endpoint returning reported private/hidden content to a moderator. Report APIs
omit reporter identity even in moderator queues.

Blocked commenters and likers are omitted from ordinary comment bodies and interaction counts, including on third-party
posts. Detail/comment policy evaluation batches at most1000 distinct actors per interaction type (at most two501-ID
policy calls), then filters in SQL before pagination/counts. Above this bound the endpoint fails closed with503 instead
of returning incomplete or potentially disclosing counts. Feed items do not embed interaction counts and use bounded
author policy batches.

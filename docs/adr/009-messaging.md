# 009: One-to-one messaging

Accepted 2026-09-27. One canonical sorted pair is protected by a database unique constraint. The conversation row
serializes message sequence allocation, per-conversation sender limits and read-position changes. Client-generated UUIDs
are unique per conversation and sender; same ID/text returns the original result, changed text conflicts. A retry of an
already committed message remains idempotent after a block; no new message is written.

Current member policy is checked on new conversation creation and every new send. Historical reads need participant
membership, not a current relationship. A moderator role does not bypass that check. Messages are text only, max4000
UTF-16 code units, stored as CLOB; no editing, deletion, attachments or end-to-end encryption claim.60 new messages per
sender/conversation/minute, enforced under the conversation lock, is an abuse control rather than a global member rate
limit.

Message history uses a conversation-bound opaque sequence cursor, ordered ascending. The returned nextCursor is also the
polling position, including on an empty result. Read commands carry a messageId from that conversation and take max
(old,requested sequence). Unread counts count incoming messages beyond that position, independent of notification state.
Conversation lists use creation time/id cursors and one indexed SQL query for counts; no downstream call per
conversation/message.

Message text never enters outbox events or notifications. Generic message.sent events use conversationId as
aggregate/key and sequence as aggregateVersion. Publication can be duplicated or reordered during recovery;
notifications are historical and do not mutate authoritative message/read state. A future WebSocket layer may deliver
hints/cursors while REST and durable history remain authoritative; no socket implementation in MVP-2.

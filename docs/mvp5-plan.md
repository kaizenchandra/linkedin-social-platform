# MVP-5 plan

Preserve the seven services and MVP-4 API behavior. No framework upgrade or new infrastructure. The small development
client is a verification tool, not a product frontend.

| Phase | Gate                                                                                                    | Status |
|-------|---------------------------------------------------------------------------------------------------------|--------|
| 0     | Current baseline; replay ordering, conservative snapshot boundary, finite retention and resource limits | PASS   |
| 1     | Transactional durable history; rollback, concurrent commits, owner cursors and retention                | PASS   |
| 2     | Authenticated live messaging; reconnect, heartbeat, expiry and bounded transport                        | PASS   |
| 3     | Notification stream and idempotent multi-device reads                                                   | PASS   |
| 4     | Private mute/archive and transactional message-triggered unarchive                                      | PASS   |
| 5     | Two replicas, failure/replay, slow clients, measured load and old regressions                           | PASS   |
| 6     | Additive populated upgrade, Compose/kind, fresh setup, CI/docs and restart recovery                     | PASS   |

Add service-owned migrations after messaging V2 and notification V5. Shared code may contain narrowly scoped technical
cursor/replay/transport mechanics; no shared business entities. Business services supply events and state
synchronization. Update contracts and narrow internal scopes before producer rollout. Upgrade uses a maintenance window;
rollback does not undo schema/data or expanded events.

See [protocol](realtime-protocol.md), [checkpoint](mvp5-progress.md) and [verification](mvp5-verification.md).

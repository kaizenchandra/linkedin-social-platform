# 013: Durable owner streams over SSE

Accepted 2026-09-27 for MVP-5. Keep MVC business services and use Servlet asynchronous nonblocking output, not another reactive server stack. Ordinary REST commands remain authoritative.

Per-owner Oracle head locks serialize event positions through commit. Replay is stored atomically with the domain transaction. Sorted owner locks avoid opposing two-participant sends deadlocking on stream heads. Independent instance polling avoids incorrect Kafka consumer-group broadcast assumptions. No additional broker or wake-up service is needed.

A synchronization boundary is captured before authoritative state reads. Replay from that boundary covers concurrent state changes; clients merge/refetch idempotently. This intentionally permits duplicates rather than claiming a frozen snapshot across paginated reads. Cleanup advances only a contiguous retained floor; expired cursors force reset.

Servlet nonblocking writes give a bounded application buffer and pending-write deadline without a thread blocked indefinitely on a slow peer. Instance-local admission, finite lifetime, JWT expiry, a bounded database executor and gateway limits define local resource bounds. Live connection count is not delivery/read acknowledgement.

See [protocol](../realtime-protocol.md) for exact limits and API semantics. Spring Batch remains deferred; pruning is small bounded technical maintenance without a bulk job requirement. No framework version upgrade, WebSocket, shared business entity or new service.

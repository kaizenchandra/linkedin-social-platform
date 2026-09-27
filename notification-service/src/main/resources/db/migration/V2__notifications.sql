CREATE TABLE consumed_events
(
    event_id    VARCHAR2(36 CHAR) PRIMARY KEY,
    consumed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE TABLE notifications
(
    id           VARCHAR2(36 CHAR) PRIMARY KEY,
    event_id     VARCHAR2(36 CHAR) NOT NULL UNIQUE REFERENCES consumed_events(event_id),
    recipient_id VARCHAR2(36 CHAR) NOT NULL,
    actor_id     VARCHAR2(36 CHAR) NOT NULL,
    resource_id  VARCHAR2(36 CHAR) NOT NULL,
    event_type   VARCHAR2(64 CHAR) NOT NULL,
    occurred_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    read_at      TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX ix_notifications_owner ON notifications (recipient_id, occurred_at DESC, id DESC);

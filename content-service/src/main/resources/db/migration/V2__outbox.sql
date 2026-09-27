CREATE TABLE outbox (
 id VARCHAR2(36 CHAR) PRIMARY KEY, aggregate_id VARCHAR2(36 CHAR) NOT NULL,
 envelope CLOB NOT NULL, trace_parent VARCHAR2(255 CHAR),
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, delivered_at TIMESTAMP(6) WITH TIME ZONE,
 attempts NUMBER(10) DEFAULT 0 NOT NULL, next_attempt_at TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX ix_outbox_pending ON outbox(delivered_at,next_attempt_at,created_at);

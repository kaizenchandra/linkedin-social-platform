CREATE TABLE stream_heads (
 owner_id VARCHAR2(36 CHAR) PRIMARY KEY,
 last_position NUMBER(19) DEFAULT 0 NOT NULL,
 retained_floor NUMBER(19) DEFAULT 0 NOT NULL,
 CONSTRAINT ck_stream_position CHECK(retained_floor>=0 AND last_position>=retained_floor)
);
CREATE TABLE stream_events (
 owner_id VARCHAR2(36 CHAR) NOT NULL REFERENCES stream_heads(owner_id),
 position NUMBER(19) NOT NULL,
 event_id VARCHAR2(36 CHAR) NOT NULL UNIQUE,
 event_type VARCHAR2(60 CHAR) NOT NULL,
 resource_id VARCHAR2(36 CHAR) NOT NULL,
 resource_version NUMBER(19) NOT NULL,
 occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 PRIMARY KEY(owner_id,position),
 CONSTRAINT ck_stream_event_position CHECK(position>0 AND resource_version>=0)
);
CREATE INDEX ix_stream_retention ON stream_events(occurred_at,owner_id,position);

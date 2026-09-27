CREATE TABLE connections
(
    id           VARCHAR2(36 CHAR) PRIMARY KEY,
    low_id       VARCHAR2(36 CHAR) NOT NULL REFERENCES members(id),
    high_id      VARCHAR2(36 CHAR) NOT NULL REFERENCES members(id),
    requester_id VARCHAR2(36 CHAR) NOT NULL REFERENCES members(id),
    state        VARCHAR2(16 CHAR) NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version      NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT uq_connection_pair UNIQUE (low_id, high_id),
    CONSTRAINT ck_connection_pair CHECK (low_id < high_id),
    CONSTRAINT ck_connection_requester CHECK (requester_id = low_id OR requester_id = high_id),
    CONSTRAINT ck_connection_state CHECK (state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'REMOVED'))
);
CREATE INDEX ix_connection_low ON connections (low_id, state, updated_at, id);
CREATE INDEX ix_connection_high ON connections (high_id, state, updated_at, id);

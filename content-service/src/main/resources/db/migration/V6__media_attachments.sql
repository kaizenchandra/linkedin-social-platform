CREATE TABLE media_operations
(
    id          VARCHAR2(36 CHAR) PRIMARY KEY,
    resource_id VARCHAR2(36 CHAR) NOT NULL,
    actor_id    VARCHAR2(36 CHAR) NOT NULL,
    media_ids   VARCHAR2(200 CHAR),
    state       VARCHAR2(16 CHAR) NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_media_operation_state CHECK (state IN ('PREPARED', 'COMMITTED', 'ABORTED'))
);
CREATE INDEX ix_media_op_resource ON media_operations (resource_id, state, created_at);
CREATE TABLE media_references
(
    resource_id VARCHAR2(36 CHAR) NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    media_id    VARCHAR2(36 CHAR) NOT NULL UNIQUE,
    position    NUMBER(2) NOT NULL,
    PRIMARY KEY (resource_id, position)
);

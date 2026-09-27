CREATE TABLE media_objects
(
    id                  VARCHAR2(36 CHAR) PRIMARY KEY,
    owner_id            VARCHAR2(36 CHAR) NOT NULL,
    object_key          VARCHAR2(80 CHAR) NOT NULL UNIQUE,
    state               VARCHAR2(20 CHAR) NOT NULL,
    content_type        VARCHAR2(40 CHAR),
    byte_size           NUMBER(19),
    width               NUMBER(10),
    height              NUMBER(10),
    resource_type       VARCHAR2(16 CHAR),
    resource_id         VARCHAR2(36 CHAR),
    operation_id        VARCHAR2(36 CHAR),
    operation_media_ids VARCHAR2(200 CHAR),
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version             NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT ck_media_state CHECK (state IN ('TEMPORARY', 'READY', 'CLAIMED', 'ATTACHED', 'DELETE_PENDING')),
    CONSTRAINT ck_media_type CHECK (resource_type IN ('PROFILE', 'POST'))
);
CREATE INDEX ix_media_cleanup ON media_objects (state, updated_at, id);
CREATE INDEX ix_media_owner ON media_objects (owner_id, created_at);

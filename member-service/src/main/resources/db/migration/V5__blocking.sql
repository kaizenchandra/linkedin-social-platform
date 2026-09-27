CREATE TABLE member_blocks (
 id VARCHAR2(36 CHAR) PRIMARY KEY,
 blocker_id VARCHAR2(36 CHAR) NOT NULL REFERENCES members(id),
 blocked_id VARCHAR2(36 CHAR) NOT NULL REFERENCES members(id),
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_member_block UNIQUE(blocker_id,blocked_id),
 CONSTRAINT ck_member_block CHECK(blocker_id<>blocked_id)
);
CREATE INDEX ix_block_reverse ON member_blocks(blocked_id,blocker_id);

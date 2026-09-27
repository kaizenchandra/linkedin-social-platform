ALTER TABLE conversations ADD (low_read_version NUMBER(19) DEFAULT 0 NOT NULL, high_read_version NUMBER(19) DEFAULT 0 NOT NULL);
CREATE TABLE conversation_preferences
(
    conversation_id VARCHAR2(36 CHAR) NOT NULL REFERENCES conversations(id),
    member_id       VARCHAR2(36 CHAR) NOT NULL,
    muted           NUMBER(1) DEFAULT 0 NOT NULL CHECK(muted IN(0,1)),
    archived        NUMBER(1) DEFAULT 0 NOT NULL CHECK(archived IN(0,1)),
    version         NUMBER(19) DEFAULT 0 NOT NULL,
    PRIMARY KEY (conversation_id, member_id)
);
INSERT INTO conversation_preferences(conversation_id, member_id)
SELECT id, low_id
FROM conversations;
INSERT INTO conversation_preferences(conversation_id, member_id)
SELECT id, high_id
FROM conversations;
CREATE INDEX ix_conversation_preferences_owner ON conversation_preferences (member_id, archived, conversation_id);

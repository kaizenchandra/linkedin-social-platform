CREATE TABLE conversations (
 id VARCHAR2(36 CHAR) PRIMARY KEY,
 low_id VARCHAR2(36 CHAR) NOT NULL, high_id VARCHAR2(36 CHAR) NOT NULL,
 last_sequence NUMBER(19) DEFAULT 0 NOT NULL,
 low_read NUMBER(19) DEFAULT 0 NOT NULL, high_read NUMBER(19) DEFAULT 0 NOT NULL,
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_conversation_pair UNIQUE(low_id,high_id),
 CONSTRAINT ck_conversation_pair CHECK(low_id<high_id),
 CONSTRAINT ck_conversation_reads CHECK(low_read>=0 AND high_read>=0 AND low_read<=last_sequence AND high_read<=last_sequence)
);
CREATE INDEX ix_conversation_low ON conversations(low_id,created_at,id);
CREATE INDEX ix_conversation_high ON conversations(high_id,created_at,id);
CREATE TABLE messages (
 id VARCHAR2(36 CHAR) PRIMARY KEY, conversation_id VARCHAR2(36 CHAR) NOT NULL REFERENCES conversations(id),
 sender_id VARCHAR2(36 CHAR) NOT NULL, client_message_id VARCHAR2(36 CHAR) NOT NULL,
 sequence_number NUMBER(19) NOT NULL, body CLOB NOT NULL,
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_message_client UNIQUE(conversation_id,sender_id,client_message_id),
 CONSTRAINT uq_message_sequence UNIQUE(conversation_id,sequence_number),
 CONSTRAINT ck_message_sequence CHECK(sequence_number>0)
);
CREATE INDEX ix_message_rate ON messages(conversation_id,sender_id,created_at);

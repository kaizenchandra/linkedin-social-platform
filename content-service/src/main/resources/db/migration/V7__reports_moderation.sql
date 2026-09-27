ALTER TABLE posts ADD (hidden NUMBER(1) DEFAULT 0 NOT NULL, deleted_at TIMESTAMP(6) WITH TIME ZONE);
ALTER TABLE posts ADD CONSTRAINT ck_post_hidden CHECK(hidden IN (0,1));
ALTER TABLE comments ADD (hidden NUMBER(1) DEFAULT 0 NOT NULL, deleted_at TIMESTAMP(6) WITH TIME ZONE);
ALTER TABLE comments ADD CONSTRAINT ck_comment_hidden CHECK(hidden IN (0,1));
CREATE TABLE content_reports (
 id VARCHAR2(36 CHAR) PRIMARY KEY, reporter_id VARCHAR2(36 CHAR) NOT NULL,
 target_type VARCHAR2(16 CHAR) NOT NULL, target_id VARCHAR2(36 CHAR) NOT NULL,
 reason VARCHAR2(24 CHAR) NOT NULL, explanation VARCHAR2(1000 CHAR),
 state VARCHAR2(16 CHAR) DEFAULT 'OPEN' NOT NULL, created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 CONSTRAINT ck_report_type CHECK(target_type IN ('POST','COMMENT')),
 CONSTRAINT ck_report_reason CHECK(reason IN ('SPAM','HARASSMENT','INAPPROPRIATE','OTHER')),
 CONSTRAINT ck_report_state CHECK(state IN ('OPEN','DISMISSED'))
);
CREATE UNIQUE INDEX uq_active_report ON content_reports (
 CASE WHEN state='OPEN' THEN reporter_id END,
 CASE WHEN state='OPEN' THEN target_type END,
 CASE WHEN state='OPEN' THEN target_id END
);
CREATE INDEX ix_report_queue ON content_reports(state,created_at,id);
CREATE INDEX ix_report_owner ON content_reports(reporter_id,created_at,id);
CREATE TABLE moderation_audit (
 id VARCHAR2(36 CHAR) PRIMARY KEY, actor_id VARCHAR2(36 CHAR) NOT NULL,
 report_id VARCHAR2(36 CHAR) NOT NULL REFERENCES content_reports(id),
 target_type VARCHAR2(16 CHAR) NOT NULL,target_id VARCHAR2(36 CHAR) NOT NULL,
 action VARCHAR2(16 CHAR) NOT NULL, reason VARCHAR2(1000 CHAR) NOT NULL,
 occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 CONSTRAINT ck_audit_action CHECK(action IN ('INSPECT','HIDE','RESTORE','DISMISS'))
);
CREATE INDEX ix_audit_report ON moderation_audit(report_id,occurred_at,id);

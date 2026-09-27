CREATE TABLE job_reports (
 id VARCHAR2(36 CHAR) PRIMARY KEY, job_id VARCHAR2(36 CHAR) NOT NULL REFERENCES jobs(id),
 reporter_id VARCHAR2(36 CHAR) NOT NULL, reason VARCHAR2(24 CHAR) NOT NULL,
 explanation VARCHAR2(1000 CHAR), state VARCHAR2(16 CHAR) DEFAULT 'OPEN' NOT NULL,
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 CONSTRAINT ck_job_report_reason CHECK(reason IN ('SPAM','SUSPECTED_FRAUD','INAPPROPRIATE','OTHER')),
 CONSTRAINT ck_job_report_state CHECK(state IN ('OPEN','DISMISSED'))
);
CREATE UNIQUE INDEX uq_job_report_active ON job_reports(CASE WHEN state='OPEN' THEN reporter_id END,CASE WHEN state='OPEN' THEN job_id END);
CREATE INDEX ix_job_report_queue ON job_reports(state,created_at DESC,id DESC);
CREATE INDEX ix_job_report_owner ON job_reports(reporter_id,created_at DESC,id DESC);
CREATE INDEX ix_job_report_job ON job_reports(job_id);
CREATE TABLE hiring_moderation_audit (
 id VARCHAR2(36 CHAR) PRIMARY KEY, report_id VARCHAR2(36 CHAR) NOT NULL REFERENCES job_reports(id),
 actor_id VARCHAR2(36 CHAR) NOT NULL, action VARCHAR2(16 CHAR) NOT NULL,
 reason VARCHAR2(1000 CHAR) NOT NULL, occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_hiring_moderation_audit ON hiring_moderation_audit(report_id,occurred_at DESC,id DESC);

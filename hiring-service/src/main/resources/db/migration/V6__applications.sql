ALTER TABLE jobs ADD CONSTRAINT uq_job_company_id UNIQUE(company_id,id);
CREATE TABLE job_applications (
 id VARCHAR2(36 CHAR) PRIMARY KEY, company_id VARCHAR2(36 CHAR) NOT NULL REFERENCES companies(id),
 job_id VARCHAR2(36 CHAR) NOT NULL, applicant_id VARCHAR2(36 CHAR) NOT NULL,
 submission_key VARCHAR2(36 CHAR) NOT NULL, request_json CLOB NOT NULL,
 cover_note CLOB, profile_snapshot CLOB NOT NULL, job_snapshot CLOB NOT NULL,
 company_name VARCHAR2(120 CHAR) NOT NULL, state VARCHAR2(16 CHAR) DEFAULT 'SUBMITTED' NOT NULL,
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 version NUMBER(19) DEFAULT 0 NOT NULL,
 CONSTRAINT fk_application_company_job FOREIGN KEY(company_id,job_id) REFERENCES jobs(company_id,id),
 CONSTRAINT uq_applicant_job UNIQUE(applicant_id,job_id),
 CONSTRAINT uq_submission_key UNIQUE(applicant_id,submission_key),
 CONSTRAINT ck_application_state CHECK(state IN ('SUBMITTED','IN_REVIEW','SHORTLISTED','REJECTED','WITHDRAWN')),
 CONSTRAINT ck_application_request CHECK(request_json IS JSON),
 CONSTRAINT ck_application_profile CHECK(profile_snapshot IS JSON),
 CONSTRAINT ck_application_job CHECK(job_snapshot IS JSON)
);
CREATE INDEX ix_application_owner ON job_applications(applicant_id,created_at DESC,id DESC);
CREATE INDEX ix_application_company ON job_applications(company_id,state,created_at DESC,id DESC);
CREATE INDEX ix_application_job ON job_applications(company_id,job_id,state,created_at DESC,id DESC);
CREATE TABLE application_history (
 id VARCHAR2(36 CHAR) PRIMARY KEY, application_id VARCHAR2(36 CHAR) NOT NULL REFERENCES job_applications(id),
 actor_id VARCHAR2(36 CHAR) NOT NULL, from_state VARCHAR2(16 CHAR), to_state VARCHAR2(16 CHAR) NOT NULL,
 occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, application_version NUMBER(19) NOT NULL,
 CONSTRAINT uq_application_history UNIQUE(application_id,application_version),
 CONSTRAINT ck_history_state CHECK(to_state IN ('SUBMITTED','IN_REVIEW','SHORTLISTED','REJECTED','WITHDRAWN'))
);
CREATE INDEX ix_application_history ON application_history(application_id,occurred_at,id);

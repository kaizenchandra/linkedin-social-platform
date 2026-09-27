CREATE TABLE alert_epoch (singleton NUMBER(1) PRIMARY KEY CHECK(singleton=1), epoch NUMBER(19) NOT NULL);
INSERT INTO alert_epoch(singleton,epoch) VALUES(1,0);
CREATE TABLE saved_searches (
 id VARCHAR2(36 CHAR) PRIMARY KEY, member_id VARCHAR2(36 CHAR) NOT NULL,
 name VARCHAR2(100 CHAR) NOT NULL, keywords VARCHAR2(100 CHAR), location VARCHAR2(150 CHAR),
 work_arrangement VARCHAR2(16 CHAR), employment_type VARCHAR2(16 CHAR),
 enabled NUMBER(1) NOT NULL CHECK(enabled IN(0,1)), deleted NUMBER(1) DEFAULT 0 NOT NULL CHECK(deleted IN(0,1)),
 criteria_version NUMBER(19) DEFAULT 1 NOT NULL, version NUMBER(19) DEFAULT 0 NOT NULL,
 criteria_epoch NUMBER(19) NOT NULL, activation_epoch NUMBER(19) NOT NULL,
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 CONSTRAINT ck_search_work CHECK(work_arrangement IN('ONSITE','HYBRID','REMOTE')),
 CONSTRAINT ck_search_employment CHECK(employment_type IN('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP'))
);
CREATE INDEX ix_search_owner ON saved_searches(member_id,deleted,id);
CREATE INDEX ix_search_matching ON saved_searches(enabled,deleted,member_id,criteria_epoch,activation_epoch);
CREATE TABLE saved_search_companies (
 search_id VARCHAR2(36 CHAR) NOT NULL REFERENCES saved_searches(id),
 company_id VARCHAR2(36 CHAR) NOT NULL REFERENCES companies(id), PRIMARY KEY(search_id,company_id)
);
CREATE INDEX ix_search_company ON saved_search_companies(company_id,search_id);
CREATE TABLE job_publications (
 job_id VARCHAR2(36 CHAR) PRIMARY KEY REFERENCES jobs(id), company_id VARCHAR2(36 CHAR) NOT NULL REFERENCES companies(id),
 actor_id VARCHAR2(36 CHAR) NOT NULL, epoch NUMBER(19) NOT NULL,
 published_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 title VARCHAR2(160 CHAR) NOT NULL, description CLOB NOT NULL, location VARCHAR2(150 CHAR) NOT NULL,
 work_arrangement VARCHAR2(16 CHAR) NOT NULL, employment_type VARCHAR2(16 CHAR) NOT NULL
);
CREATE INDEX ix_publication_company ON job_publications(company_id,job_id);
CREATE TABLE alert_work (
 job_id VARCHAR2(36 CHAR) PRIMARY KEY REFERENCES job_publications(job_id),
 last_member VARCHAR2(36 CHAR) DEFAULT '0' NOT NULL,
 state VARCHAR2(16 CHAR) DEFAULT 'PENDING' NOT NULL CHECK(state IN('PENDING','RETRY','FAILED','DONE')),
 attempts NUMBER(10) DEFAULT 0 NOT NULL, next_attempt_at TIMESTAMP(6) WITH TIME ZONE,
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 trace_parent VARCHAR2(55 CHAR), last_error VARCHAR2(100 CHAR)
);
CREATE INDEX ix_alert_work_ready ON alert_work(state,next_attempt_at,created_at);
CREATE TABLE job_alert_matches (
 id VARCHAR2(36 CHAR) PRIMARY KEY,
 job_id VARCHAR2(36 CHAR) NOT NULL REFERENCES job_publications(job_id), member_id VARCHAR2(36 CHAR) NOT NULL,
 search_id VARCHAR2(36 CHAR) NOT NULL REFERENCES saved_searches(id), criteria_version NUMBER(19) NOT NULL,
 activation_epoch NUMBER(19) NOT NULL, created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_job_alert_recipient UNIQUE(job_id,member_id)
);
CREATE INDEX ix_alert_match_search ON job_alert_matches(search_id,id);

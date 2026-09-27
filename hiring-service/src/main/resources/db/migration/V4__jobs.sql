CREATE TABLE jobs (
 id VARCHAR2(36 CHAR) PRIMARY KEY, company_id VARCHAR2(36 CHAR) NOT NULL REFERENCES companies(id),
 title VARCHAR2(160 CHAR) NOT NULL, description CLOB NOT NULL, location VARCHAR2(150 CHAR) NOT NULL,
 work_arrangement VARCHAR2(16 CHAR) NOT NULL, employment_type VARCHAR2(16 CHAR) NOT NULL,
 salary_minimum NUMBER(19,2), salary_maximum NUMBER(19,2), salary_currency VARCHAR2(3 CHAR), pay_period VARCHAR2(16 CHAR),
 deadline TIMESTAMP(6) WITH TIME ZONE, state VARCHAR2(16 CHAR) DEFAULT 'DRAFT' NOT NULL,
 hidden NUMBER(1) DEFAULT 0 NOT NULL, created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, published_at TIMESTAMP(6) WITH TIME ZONE,
 version NUMBER(19) DEFAULT 0 NOT NULL,
 CONSTRAINT ck_job_state CHECK(state IN ('DRAFT','PUBLISHED','CLOSED')),
 CONSTRAINT ck_job_work CHECK(work_arrangement IN ('ONSITE','HYBRID','REMOTE')),
 CONSTRAINT ck_job_employment CHECK(employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP')),
 CONSTRAINT ck_job_hidden CHECK(hidden IN (0,1)),
 CONSTRAINT ck_job_pay CHECK(pay_period IN ('HOUR','MONTH','YEAR')),
 CONSTRAINT ck_job_salary CHECK((salary_minimum IS NULL OR salary_minimum>=0) AND (salary_maximum IS NULL OR salary_maximum>=0) AND (salary_minimum IS NULL OR salary_maximum IS NULL OR salary_minimum<=salary_maximum)),
 CONSTRAINT ck_job_salary_fields CHECK(((salary_minimum IS NOT NULL OR salary_maximum IS NOT NULL) AND salary_currency IS NOT NULL AND pay_period IS NOT NULL) OR (salary_minimum IS NULL AND salary_maximum IS NULL AND salary_currency IS NULL AND pay_period IS NULL)),
 CONSTRAINT ck_job_published CHECK((state='DRAFT' AND published_at IS NULL) OR (state IN ('PUBLISHED','CLOSED') AND published_at IS NOT NULL))
);
CREATE INDEX ix_job_search ON jobs(state,hidden,published_at DESC,id DESC);
CREATE INDEX ix_job_company ON jobs(company_id,created_at DESC,id DESC);
CREATE INDEX ix_job_structured ON jobs(state,hidden,work_arrangement,employment_type,published_at DESC,id DESC);

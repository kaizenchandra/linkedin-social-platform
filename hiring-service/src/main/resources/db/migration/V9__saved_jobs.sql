CREATE TABLE saved_jobs (
 member_id VARCHAR2(36 CHAR) NOT NULL,
 job_id VARCHAR2(36 CHAR) NOT NULL REFERENCES jobs(id),
 saved_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 PRIMARY KEY(member_id,job_id)
);
CREATE INDEX ix_saved_job_target ON saved_jobs(job_id,member_id);
CREATE INDEX ix_saved_job_list ON saved_jobs(member_id,saved_at DESC,job_id DESC);

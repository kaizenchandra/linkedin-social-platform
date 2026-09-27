CREATE TABLE hiring_member_state (member_id VARCHAR2(36 CHAR) PRIMARY KEY);
CREATE TABLE company_follows (
 member_id VARCHAR2(36 CHAR) NOT NULL, company_id VARCHAR2(36 CHAR) NOT NULL REFERENCES companies(id),
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, PRIMARY KEY(member_id,company_id)
);
CREATE INDEX ix_company_follow_target ON company_follows(company_id,member_id);
CREATE INDEX ix_company_follow_list ON company_follows(member_id,created_at DESC,company_id DESC);

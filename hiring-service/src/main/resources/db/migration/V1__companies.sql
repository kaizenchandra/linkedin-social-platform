CREATE TABLE companies
(
    id           VARCHAR2(36 CHAR) PRIMARY KEY,
    owner_id     VARCHAR2(36 CHAR) NOT NULL,
    display_name VARCHAR2(120 CHAR) NOT NULL,
    slug         VARCHAR2(80 CHAR) NOT NULL UNIQUE,
    description  CLOB                        NOT NULL,
    industry     VARCHAR2(100 CHAR) NOT NULL,
    location     VARCHAR2(150 CHAR) NOT NULL,
    website      VARCHAR2(500 CHAR),
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version      NUMBER(19) DEFAULT 0 NOT NULL
);
CREATE TABLE company_members
(
    company_id VARCHAR2(36 CHAR) NOT NULL REFERENCES companies(id),
    member_id  VARCHAR2(36 CHAR) NOT NULL,
    joined_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (company_id, member_id)
);
ALTER TABLE companies
    ADD CONSTRAINT fk_company_owner FOREIGN KEY (id, owner_id)
        REFERENCES company_members (company_id, member_id) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX ix_company_member ON company_members (member_id, company_id);
CREATE INDEX ix_company_newest ON companies (created_at, id);
CREATE TABLE company_invitations
(
    id         VARCHAR2(36 CHAR) PRIMARY KEY,
    company_id VARCHAR2(36 CHAR) NOT NULL REFERENCES companies(id),
    member_id  VARCHAR2(36 CHAR) NOT NULL,
    invited_by VARCHAR2(36 CHAR) NOT NULL,
    state      VARCHAR2(16 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_invitation_state CHECK (state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED'))
);
CREATE UNIQUE INDEX uq_company_invite_active ON company_invitations (
                                                                     CASE WHEN state='PENDING' THEN company_id END,
                                                                     CASE WHEN state='PENDING' THEN member_id END);
CREATE INDEX ix_invite_member ON company_invitations (member_id, created_at, id);
CREATE INDEX ix_invite_company ON company_invitations (company_id, created_at, id);
CREATE TABLE hiring_audit
(
    id          VARCHAR2(36 CHAR) PRIMARY KEY,
    company_id  VARCHAR2(36 CHAR) NOT NULL REFERENCES companies(id),
    actor_id    VARCHAR2(36 CHAR) NOT NULL,
    action      VARCHAR2(40 CHAR) NOT NULL,
    target_id   VARCHAR2(36 CHAR) NOT NULL,
    reason      VARCHAR2(1000 CHAR) NOT NULL,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_hiring_audit ON hiring_audit (company_id, occurred_at, id);

CREATE TABLE members
(
    id           VARCHAR2(36 CHAR) PRIMARY KEY,
    display_name VARCHAR2(100 CHAR) NOT NULL,
    headline     VARCHAR2(200 CHAR),
    summary      VARCHAR2(2000 CHAR),
    location     VARCHAR2(100 CHAR),
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version      NUMBER(19) DEFAULT 0 NOT NULL
);
CREATE INDEX ix_member_name ON members (display_name, id);
CREATE TABLE experiences
(
    id          VARCHAR2(36 CHAR) PRIMARY KEY,
    member_id   VARCHAR2(36 CHAR) NOT NULL REFERENCES members(id),
    company     VARCHAR2(100 CHAR) NOT NULL,
    title       VARCHAR2(100 CHAR) NOT NULL,
    start_month VARCHAR2(7 CHAR) NOT NULL,
    end_month   VARCHAR2(7 CHAR),
    position    NUMBER(10) NOT NULL
);
CREATE INDEX ix_experience_member ON experiences (member_id, position);

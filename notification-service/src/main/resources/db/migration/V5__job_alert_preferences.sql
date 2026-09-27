CREATE TABLE job_alert_preferences
(
    member_id VARCHAR2(36 CHAR) PRIMARY KEY,
    enabled   NUMBER(1) DEFAULT 1 NOT NULL CHECK(enabled IN(0,1))
);
CREATE TABLE job_alert_deliveries
(
    member_id VARCHAR2(36 CHAR) NOT NULL,
    job_id    VARCHAR2(36 CHAR) NOT NULL,
    match_id  VARCHAR2(36 CHAR) NOT NULL UNIQUE,
    PRIMARY KEY (member_id, job_id)
);

CREATE TABLE member_follows (
 follower_id VARCHAR2(36 CHAR) NOT NULL REFERENCES members(id),
 followed_id VARCHAR2(36 CHAR) NOT NULL REFERENCES members(id),
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 PRIMARY KEY(follower_id,followed_id),
 CONSTRAINT ck_follow_self CHECK(follower_id<>followed_id)
);
CREATE INDEX ix_follow_target ON member_follows(followed_id,follower_id);
CREATE INDEX ix_follow_list ON member_follows(follower_id,created_at DESC,followed_id DESC);

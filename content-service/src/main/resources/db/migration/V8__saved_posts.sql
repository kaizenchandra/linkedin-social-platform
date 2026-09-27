CREATE TABLE saved_posts (
 member_id VARCHAR2(36 CHAR) NOT NULL,
 post_id VARCHAR2(36 CHAR) NOT NULL REFERENCES posts(id),
 saved_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 PRIMARY KEY(member_id,post_id)
);
CREATE INDEX ix_saved_post_target ON saved_posts(post_id,member_id);
CREATE INDEX ix_saved_post_list ON saved_posts(member_id,saved_at DESC,post_id DESC);

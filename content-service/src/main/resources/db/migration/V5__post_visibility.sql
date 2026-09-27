ALTER TABLE posts ADD visibility VARCHAR2(16 CHAR) DEFAULT 'MEMBERS' NOT NULL;
ALTER TABLE posts ADD CONSTRAINT ck_post_visibility CHECK(visibility IN ('MEMBERS','CONNECTIONS'));
CREATE INDEX ix_post_visibility ON posts(author_id,visibility,created_at,id);

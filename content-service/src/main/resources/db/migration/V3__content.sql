CREATE TABLE posts
(
    id         VARCHAR2(36 CHAR) PRIMARY KEY,
    author_id  VARCHAR2(36 CHAR) NOT NULL,
    body       CLOB                        NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version    NUMBER(19) DEFAULT 0 NOT NULL
);
CREATE INDEX ix_posts_author_cursor ON posts (author_id, created_at DESC, id DESC);
CREATE TABLE comments
(
    id         VARCHAR2(36 CHAR) PRIMARY KEY,
    post_id    VARCHAR2(36 CHAR) NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id  VARCHAR2(36 CHAR) NOT NULL,
    body       VARCHAR2(1000 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_comments_post ON comments (post_id, created_at, id);
CREATE TABLE post_likes
(
    id         VARCHAR2(36 CHAR) PRIMARY KEY,
    post_id    VARCHAR2(36 CHAR) NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    member_id  VARCHAR2(36 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_like UNIQUE (post_id, member_id)
);

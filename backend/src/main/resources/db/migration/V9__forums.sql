CREATE TABLE forum_categories (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description  VARCHAR(255),
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE forum_posts (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id  BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT NOT NULL,
    is_moderated BOOLEAN NOT NULL DEFAULT FALSE,
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_category FOREIGN KEY (category_id) REFERENCES forum_categories(id),
    CONSTRAINT fk_post_user     FOREIGN KEY (user_id)     REFERENCES users(id)
);
CREATE INDEX idx_post_category ON forum_posts(category_id);

CREATE TABLE forum_comments (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id      BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    comment_text TEXT NOT NULL,
    is_moderated BOOLEAN NOT NULL DEFAULT FALSE,
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES forum_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_comment_post ON forum_comments(post_id);

CREATE TABLE forum_post_upvotes (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id      BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upvote_post FOREIGN KEY (post_id) REFERENCES forum_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_upvote_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_upvote_once UNIQUE (post_id, user_id)
);

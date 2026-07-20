CREATE TABLE reviews (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       BIGINT NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    rated_user_id    BIGINT NOT NULL,
    rating           INT NOT NULL,
    comments         VARCHAR(255),
    flagged          BOOLEAN NOT NULL DEFAULT FALSE,
    created_date     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_session  FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users(id),
    CONSTRAINT fk_review_rated    FOREIGN KEY (rated_user_id) REFERENCES users(id),
    CONSTRAINT chk_review_rating  CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_review_once     UNIQUE (session_id, reviewer_user_id)
);
CREATE INDEX idx_review_rated ON reviews(rated_user_id);

ALTER TABLE sessions ADD COLUMN skill_id BIGINT NOT NULL;
ALTER TABLE sessions ADD CONSTRAINT fk_session_skill FOREIGN KEY (skill_id) REFERENCES skill(id);

CREATE TABLE skill_badge (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    skill_id      BIGINT NOT NULL,
    badge_type    VARCHAR(20) NOT NULL,
    awarded_date  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_badge_user  FOREIGN KEY (user_id)  REFERENCES users(id),
    CONSTRAINT fk_badge_skill FOREIGN KEY (skill_id) REFERENCES skill(id),
    CONSTRAINT uq_badge_once  UNIQUE (user_id, skill_id, badge_type)
);
CREATE INDEX idx_badge_user ON skill_badge(user_id);

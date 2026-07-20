CREATE TABLE skill (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_name  VARCHAR(100) NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE user_skill (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    skill_id    BIGINT NOT NULL,
    skill_type  VARCHAR(20) NOT NULL,
    experience  VARCHAR(50),
    proficiency VARCHAR(20),
    CONSTRAINT fk_us_user  FOREIGN KEY (user_id)  REFERENCES users(id),
    CONSTRAINT fk_us_skill FOREIGN KEY (skill_id) REFERENCES skill(id),
    CONSTRAINT uq_user_skill UNIQUE (user_id, skill_id, skill_type)
);
CREATE INDEX idx_user_skill_user  ON user_skill(user_id);
CREATE INDEX idx_user_skill_skill ON user_skill(skill_id);

CREATE TABLE matches (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_a_id    BIGINT NOT NULL,
    user_b_id    BIGINT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_match_a FOREIGN KEY (user_a_id) REFERENCES users(id),
    CONSTRAINT fk_match_b FOREIGN KEY (user_b_id) REFERENCES users(id)
);
CREATE INDEX idx_match_user_b ON matches(user_b_id);

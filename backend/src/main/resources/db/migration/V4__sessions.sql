CREATE TABLE sessions (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id             BIGINT NOT NULL,
    teacher_user_id      BIGINT NOT NULL,
    learner_user_id      BIGINT NOT NULL,
    scheduled_by_user_id BIGINT NOT NULL,
    session_date         DATE NOT NULL,
    start_time           TIME NOT NULL,
    end_time             TIME NOT NULL,
    mode                 VARCHAR(20) NOT NULL,
    location_or_link     VARCHAR(255),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_date         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_match   FOREIGN KEY (match_id)   REFERENCES matches(id),
    CONSTRAINT fk_session_teacher FOREIGN KEY (teacher_user_id) REFERENCES users(id),
    CONSTRAINT fk_session_learner FOREIGN KEY (learner_user_id) REFERENCES users(id),
    CONSTRAINT fk_session_sched   FOREIGN KEY (scheduled_by_user_id) REFERENCES users(id)
);
CREATE INDEX idx_session_teacher ON sessions(teacher_user_id);
CREATE INDEX idx_session_learner ON sessions(learner_user_id);

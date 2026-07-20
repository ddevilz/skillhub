CREATE TABLE notifications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    type         VARCHAR(50) NOT NULL,
    message      VARCHAR(255) NOT NULL,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_notification_user ON notifications(user_id, is_read);

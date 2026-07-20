CREATE TABLE credit_transaction (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    session_id       BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount           INT NOT NULL,
    transaction_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credittx_user    FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT fk_credittx_session FOREIGN KEY (session_id) REFERENCES sessions(id)
);
CREATE INDEX idx_credittx_user ON credit_transaction(user_id);

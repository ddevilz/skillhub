CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    city          VARCHAR(50),
    about         VARCHAR(255),
    profile_image VARCHAR(255),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_date  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_credit (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL UNIQUE,
    total_credits  INT    NOT NULL DEFAULT 10,
    credits_earned INT    NOT NULL DEFAULT 0,
    credits_spent  INT    NOT NULL DEFAULT 0,
    last_updated   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credit_user FOREIGN KEY (user_id) REFERENCES users(id)
);

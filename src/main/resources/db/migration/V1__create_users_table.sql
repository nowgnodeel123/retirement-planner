CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE,
    password      VARCHAR(255),
    nickname      VARCHAR(20)  NOT NULL,
    provider      VARCHAR(20)  NOT NULL,
    provider_id   VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_users_provider_provider_id
    ON users (provider, provider_id)
    WHERE provider_id IS NOT NULL;

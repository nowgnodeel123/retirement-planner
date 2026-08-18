CREATE TABLE audit_logs (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT,
    action           VARCHAR(10)  NOT NULL,
    entity_type      VARCHAR(50)  NOT NULL,
    request_snapshot TEXT,
    result_snapshot  TEXT,
    ip_address       VARCHAR(64),
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs (entity_type);

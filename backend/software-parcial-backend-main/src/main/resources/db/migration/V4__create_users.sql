CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(200),
    role VARCHAR(50) NOT NULL,
    department VARCHAR(150),
    avatar_url TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_role CHECK (role IN ('ADMINISTRADOR', 'REVISOR', 'SOLICITANTE'))
);

CREATE INDEX idx_users_enabled ON users(enabled);
CREATE INDEX idx_users_role ON users(role);

CREATE TABLE app_schema_check (
    id INTEGER PRIMARY KEY,
    description VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_schema_check (id, description)
VALUES (1, 'PostgreSQL infrastructure initialized');

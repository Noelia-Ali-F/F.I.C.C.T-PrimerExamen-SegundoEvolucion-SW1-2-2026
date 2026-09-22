CREATE TABLE departments (
 id VARCHAR(64) PRIMARY KEY,
 name VARCHAR(150) NOT NULL UNIQUE,
 description TEXT,
 created_by VARCHAR(160),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP,
 updated_at TIMESTAMP
);
CREATE INDEX idx_departments_active_name ON departments(active,name);

CREATE TABLE uml_collaboration_sessions (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    invite_code VARCHAR(100) NOT NULL UNIQUE,
    created_by VARCHAR(64) NOT NULL,
    invite_role VARCHAR(30) NOT NULL DEFAULT 'EDITOR',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    model_snapshot JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_uml_collaboration_invite_role CHECK (invite_role IN ('EDITOR', 'VIEWER'))
);

CREATE INDEX idx_uml_collaboration_project ON uml_collaboration_sessions(project_id);
CREATE INDEX idx_uml_collaboration_invite ON uml_collaboration_sessions(invite_code);
CREATE INDEX idx_uml_collaboration_active ON uml_collaboration_sessions(active);
CREATE INDEX idx_uml_collaboration_created_by ON uml_collaboration_sessions(created_by);

CREATE TABLE projects (
    id VARCHAR(128) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    status VARCHAR(30) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_projects_owner_user_id ON projects(owner_user_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_projects_updated_at ON projects(updated_at);

CREATE TABLE project_members (
    project_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    member_role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_members_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_project_members_user_id ON project_members(user_id);

CREATE TABLE uml_diagrams (
    id VARCHAR(128) PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL UNIQUE,
    model JSONB NOT NULL,
    model_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_uml_diagrams_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

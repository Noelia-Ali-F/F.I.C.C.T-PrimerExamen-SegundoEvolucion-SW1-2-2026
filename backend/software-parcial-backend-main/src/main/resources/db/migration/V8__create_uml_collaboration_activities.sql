CREATE TABLE uml_collaboration_activities (
    event_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES uml_collaboration_sessions(id) ON DELETE CASCADE,
    project_id VARCHAR(128) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL,
    user_name VARCHAR(180) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    element_id VARCHAR(128),
    element_type VARCHAR(30),
    operation VARCHAR(255) NOT NULL,
    event_data JSONB,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_uml_activity_session_time
    ON uml_collaboration_activities(session_id, occurred_at DESC);
CREATE INDEX idx_uml_activity_project_time
    ON uml_collaboration_activities(project_id, occurred_at DESC);


-- Receipts are stored beside the canonical diagram, not conversation memory.
-- Their atomic commit prevents a retry from applying the same UML mutation twice.
CREATE TABLE ai_model_mutation_receipts (
    project_id VARCHAR(128) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    request_id VARCHAR(100) NOT NULL,
    author_id VARCHAR(256) NOT NULL,
    instruction TEXT NOT NULL,
    answer JSONB NOT NULL,
    diagram_version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, request_id)
);

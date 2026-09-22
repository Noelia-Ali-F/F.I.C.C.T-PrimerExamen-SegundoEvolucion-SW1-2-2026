CREATE TABLE application_documents (
 storage_key VARCHAR(260) PRIMARY KEY,
 collection_name VARCHAR(100) NOT NULL,
 document_id VARCHAR(160) NOT NULL,
 payload JSONB NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_application_document UNIQUE(collection_name,document_id)
);
CREATE INDEX idx_application_documents_collection ON application_documents(collection_name);

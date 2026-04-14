-- Creates the documents table. Depends on users (FK).
CREATE TABLE IF NOT EXISTS documents (
    id              BIGSERIAL    PRIMARY KEY,
    filename        VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    s3_key          VARCHAR(500) NOT NULL UNIQUE,
    s3_url          VARCHAR(1000),
    content_type    VARCHAR(100),
    file_size       BIGINT,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    document_hash   VARCHAR(64),
    extracted_data  JSONB,
    error_message   TEXT,
    uploaded_by     BIGINT       NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);
CREATE INDEX idx_documents_status      ON documents(status);
CREATE INDEX idx_documents_hash        ON documents(document_hash);
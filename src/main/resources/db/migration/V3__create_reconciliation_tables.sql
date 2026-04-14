-- Creates reconciliation and reference tables. Depends on documents + users.
CREATE TABLE IF NOT EXISTS reconciliation_results (
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT    NOT NULL REFERENCES documents(id),
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    matched          BOOLEAN,
    mismatch_details JSONB,
    reference_data   JSONB,
    reconciled_by    BIGINT    REFERENCES users(id),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS reference_records (
    id              BIGSERIAL    PRIMARY KEY,
    vendor_name     VARCHAR(255),
    expected_amount NUMERIC(15,2),
    currency        VARCHAR(10),
    invoice_date    DATE,
    reference_id    VARCHAR(100) UNIQUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_document_id ON reconciliation_results(document_id);
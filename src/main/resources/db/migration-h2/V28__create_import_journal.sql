CREATE TABLE data_snapshots (
    id CHAR(36) PRIMARY KEY, fingerprint CHAR(64) NOT NULL UNIQUE,
    source_fingerprint CHAR(64) NOT NULL, canonical_checksum CHAR(64) NOT NULL,
    metadata_checksum CHAR(64) NOT NULL, policy_version VARCHAR(64) NOT NULL,
    base_quarter_key INT NOT NULL, chunk_size INT NOT NULL, expected_cells BIGINT NOT NULL,
    total_chunks INT NOT NULL, committed_cells BIGINT NOT NULL DEFAULT 0,
    committed_chunks INT NOT NULL DEFAULT 0, state VARCHAR(16) NOT NULL,
    manifest_json CLOB NOT NULL, companies_json CLOB NOT NULL, industries_json CLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE data_snapshot_sources (
    snapshot_id CHAR(36) NOT NULL, source_role VARCHAR(32) NOT NULL, source_json CLOB NOT NULL,
    PRIMARY KEY(snapshot_id,source_role), FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id)
);
CREATE TABLE import_runs (
    id CHAR(36) PRIMARY KEY, snapshot_id CHAR(36) NOT NULL, submitted_by VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL, retry_count INT NOT NULL DEFAULT 0, last_error_code VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id)
);
CREATE INDEX idx_import_runs_snapshot ON import_runs(snapshot_id);
CREATE TABLE import_chunks (
    snapshot_id CHAR(36) NOT NULL, chunk_number INT NOT NULL, planned_run_id CHAR(36) NOT NULL,
    committed_run_id CHAR(36), cell_count INT NOT NULL, payload_sha256 CHAR(64) NOT NULL,
    payload_json CLOB NOT NULL, state VARCHAR(16) NOT NULL, committed_at TIMESTAMP(6),
    PRIMARY KEY(snapshot_id,chunk_number), UNIQUE(planned_run_id,chunk_number),
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id),
    FOREIGN KEY(planned_run_id) REFERENCES import_runs(id), FOREIGN KEY(committed_run_id) REFERENCES import_runs(id)
);
CREATE TABLE import_stage_values (
    snapshot_id CHAR(36) NOT NULL, stock_code CHAR(6) NOT NULL, quarter_key INT NOT NULL,
    metric_code VARCHAR(64) NOT NULL, value_type VARCHAR(16) NOT NULL, metric_value DECIMAL(20,4),
    provenance_json CLOB NOT NULL, PRIMARY KEY(snapshot_id,stock_code,quarter_key,metric_code,value_type),
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id)
);
CREATE TABLE import_errors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, run_id CHAR(36) NOT NULL, error_code VARCHAR(64) NOT NULL,
    disposition VARCHAR(16) NOT NULL, issue_json CLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(run_id) REFERENCES import_runs(id)
);
CREATE INDEX idx_import_errors_run ON import_errors(run_id);

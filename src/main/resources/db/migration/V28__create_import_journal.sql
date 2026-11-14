CREATE TABLE data_snapshots (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    metadata_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    base_quarter_key INT NOT NULL,
    chunk_size INT NOT NULL,
    expected_cells BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    committed_cells BIGINT NOT NULL DEFAULT 0,
    committed_chunks INT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL,
    manifest_json LONGTEXT NOT NULL,
    companies_json LONGTEXT NOT NULL,
    industries_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE data_snapshot_sources (
    snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_role VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_json LONGTEXT NOT NULL,
    PRIMARY KEY(snapshot_id, source_role),
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id)
);
CREATE TABLE import_runs (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    submitted_by VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id),
    INDEX idx_import_runs_snapshot(snapshot_id)
);
CREATE TABLE import_chunks (
    snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    chunk_number INT NOT NULL,
    planned_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    committed_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    cell_count INT NOT NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_json LONGTEXT NOT NULL,
    state VARCHAR(16) NOT NULL,
    committed_at TIMESTAMP(6) NULL,
    PRIMARY KEY(snapshot_id, chunk_number),
    UNIQUE KEY uk_import_run_chunk(planned_run_id, chunk_number),
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id),
    FOREIGN KEY(planned_run_id) REFERENCES import_runs(id),
    FOREIGN KEY(committed_run_id) REFERENCES import_runs(id)
);
CREATE TABLE import_stage_values (
    snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stock_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quarter_key INT NOT NULL,
    metric_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    value_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    metric_value DECIMAL(20,4) NULL,
    provenance_json LONGTEXT NOT NULL,
    PRIMARY KEY(snapshot_id, stock_code, quarter_key, metric_code, value_type),
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id)
);
CREATE TABLE import_errors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    disposition VARCHAR(16) NOT NULL,
    issue_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY(run_id) REFERENCES import_runs(id),
    INDEX idx_import_errors_run(run_id)
);

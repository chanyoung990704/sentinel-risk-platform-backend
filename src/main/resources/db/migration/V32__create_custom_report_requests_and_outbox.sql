CREATE TABLE report_admission (
 scope_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 used_count INT NOT NULL DEFAULT 0 CHECK (used_count >= 0)
);
INSERT INTO report_admission(scope_key,used_count) VALUES('GLOBAL',0);
CREATE TABLE report_requests (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id BIGINT NOT NULL, company_id BIGINT NOT NULL, target_quarter_key INT NOT NULL,
 snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 idempotency_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 additional_instructions LONGTEXT NOT NULL,
 status VARCHAR(20) NOT NULL, state_version BIGINT NOT NULL DEFAULT 1,
 admission_slot_held BOOLEAN NOT NULL DEFAULT TRUE,
 last_error_code VARCHAR(64), retry_at TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_report_request_user_key(user_id,idempotency_key_hash),
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(company_id) REFERENCES companies(id),
 FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id),
 CHECK (status IN ('ACCEPTED','ENQUEUED','PROCESSING','RETRY_WAIT','SUCCEEDED','FAILED'))
) DEFAULT CHARSET=utf8mb4;
CREATE TABLE outbox_events (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 aggregatetype VARCHAR(64) NOT NULL,
 aggregateid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 type VARCHAR(64) NOT NULL, destination VARCHAR(190) NOT NULL,
 payload LONGTEXT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_outbox_aggregate(aggregateid,created_at),
 CHECK (destination IN ('sentinel.custom-report.request.v1','sentinel.custom-report.status.v1','sentinel.custom-report.dlt.v1'))
) DEFAULT CHARSET=utf8mb4;

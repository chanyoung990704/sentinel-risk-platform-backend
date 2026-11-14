CREATE TABLE report_admission(scope_key VARCHAR(80) PRIMARY KEY,used_count INT NOT NULL DEFAULT 0 CHECK(used_count>=0));
INSERT INTO report_admission(scope_key,used_count) VALUES('GLOBAL',0);
CREATE TABLE report_requests (
 id CHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, company_id BIGINT NOT NULL, target_quarter_key INT NOT NULL,
 snapshot_id CHAR(36) NOT NULL, idempotency_key_hash CHAR(64) NOT NULL, input_hash CHAR(64) NOT NULL,
 additional_instructions CLOB NOT NULL, status VARCHAR(20) NOT NULL, state_version BIGINT NOT NULL DEFAULT 1,
 admission_slot_held BOOLEAN NOT NULL DEFAULT TRUE, last_error_code VARCHAR(64),retry_at TIMESTAMP(6),
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_report_request_user_key UNIQUE(user_id,idempotency_key_hash),
 FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(company_id) REFERENCES companies(id),FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id),
 CHECK(status IN ('ACCEPTED','ENQUEUED','PROCESSING','RETRY_WAIT','SUCCEEDED','FAILED'))
);
CREATE TABLE outbox_events (
 id CHAR(36) PRIMARY KEY,aggregatetype VARCHAR(64) NOT NULL,aggregateid CHAR(36) NOT NULL,
 type VARCHAR(64) NOT NULL,destination VARCHAR(190) NOT NULL,payload CLOB NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(destination IN ('sentinel.custom-report.request.v1','sentinel.custom-report.status.v1','sentinel.custom-report.dlt.v1'))
);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregateid,created_at);

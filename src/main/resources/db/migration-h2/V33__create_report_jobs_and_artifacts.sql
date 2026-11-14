CREATE TABLE inbox_events (
 consumer VARCHAR(80) NOT NULL,event_id CHAR(36) NOT NULL,request_id CHAR(36) NOT NULL,payload_hash CHAR(64) NOT NULL,
 received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(consumer,event_id),
 FOREIGN KEY(request_id) REFERENCES report_requests(id) ON DELETE CASCADE
);
CREATE TABLE report_jobs (
 id CHAR(36) PRIMARY KEY,request_id CHAR(36) NOT NULL UNIQUE,state VARCHAR(20) NOT NULL DEFAULT 'READY',version BIGINT NOT NULL DEFAULT 0,
 generation INT NOT NULL DEFAULT 1,attempt_no INT NOT NULL DEFAULT 0,attempt_id CHAR(36),lease_token CHAR(36),owner VARCHAR(128),
 lease_until TIMESTAMP(6),retry_at TIMESTAMP(6),last_error_code VARCHAR(64),
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(request_id) REFERENCES report_requests(id) ON DELETE CASCADE,
 CHECK(state IN ('READY','RUNNING','RETRY_WAIT','SUCCEEDED','FAILED')),CHECK(generation>=1 AND attempt_no>=0)
);
CREATE INDEX idx_report_job_ready ON report_jobs(state,retry_at,created_at);
CREATE INDEX idx_report_job_lease ON report_jobs(state,lease_until);
CREATE TABLE report_attempts (
 id CHAR(36) PRIMARY KEY,job_id CHAR(36) NOT NULL,generation INT NOT NULL,attempt_no INT NOT NULL,claim_version BIGINT NOT NULL,
 lease_token CHAR(36) NOT NULL,state VARCHAR(20) NOT NULL,error_code VARCHAR(64),started_at TIMESTAMP(6) NOT NULL,completed_at TIMESTAMP(6),
 CONSTRAINT uk_report_attempt UNIQUE(job_id,generation,attempt_no),FOREIGN KEY(job_id) REFERENCES report_jobs(id) ON DELETE CASCADE
);
CREATE TABLE report_artifacts (
 id CHAR(36) PRIMARY KEY,request_id CHAR(36) NOT NULL UNIQUE,attempt_id CHAR(36) NOT NULL,generation INT NOT NULL,file_id BIGINT NOT NULL UNIQUE,
 storage_key VARCHAR(500) NOT NULL UNIQUE,file_size BIGINT NOT NULL,sha256 CHAR(64) NOT NULL,content_type VARCHAR(100) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(request_id) REFERENCES report_requests(id) ON DELETE CASCADE,FOREIGN KEY(attempt_id) REFERENCES report_attempts(id) ON DELETE CASCADE,FOREIGN KEY(file_id) REFERENCES files(id)
);
CREATE TABLE report_recovery_commands (
 id CHAR(36) PRIMARY KEY,request_id CHAR(36) NOT NULL,actor_user_id BIGINT NOT NULL,reason VARCHAR(1000) NOT NULL,target_generation INT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(request_id) REFERENCES report_requests(id) ON DELETE CASCADE,FOREIGN KEY(actor_user_id) REFERENCES users(id)
);

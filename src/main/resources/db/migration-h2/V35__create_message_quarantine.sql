ALTER TABLE outbox_events ADD COLUMN original_event_id CHAR(36) NULL;
CREATE TABLE quarantined_messages (
 id CHAR(36) PRIMARY KEY,consumer_name VARCHAR(80) NOT NULL,source_topic VARCHAR(249) NOT NULL,
 source_partition INT NOT NULL,source_offset BIGINT NOT NULL,
 body_prefix BINARY LARGE OBJECT NOT NULL,body_size INT NOT NULL,body_sha256 CHAR(64) NOT NULL,body_truncated BOOLEAN NOT NULL,
 key_prefix VARBINARY(1024),key_sha256 CHAR(64) NOT NULL,id_header VARBINARY(1024),header_sha256 CHAR(64) NOT NULL,
 claimed_event_id CHAR(36),claimed_request_id CHAR(36),reason_code VARCHAR(64) NOT NULL,dlt_event_id CHAR(36) NOT NULL,replay_event_id CHAR(36),
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_quarantine_source UNIQUE(consumer_name,source_topic,source_partition,source_offset),CHECK(source_partition>=0 AND source_offset>=0 AND body_size>=0)
);
CREATE TABLE quarantine_replay_commands (
 id CHAR(36) PRIMARY KEY,quarantine_id CHAR(36) NOT NULL,actor_user_id BIGINT NOT NULL,target_request_id CHAR(36) NOT NULL,
 reason VARCHAR(1000) NOT NULL,outcome VARCHAR(40) NOT NULL,event_id CHAR(36),created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(quarantine_id) REFERENCES quarantined_messages(id),FOREIGN KEY(actor_user_id) REFERENCES users(id)
);

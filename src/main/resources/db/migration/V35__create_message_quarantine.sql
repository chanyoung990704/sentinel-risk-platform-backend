ALTER TABLE outbox_events ADD COLUMN original_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
CREATE TABLE quarantined_messages (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 consumer_name VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_topic VARCHAR(249) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_partition INT NOT NULL,source_offset BIGINT NOT NULL,
 body_prefix LONGBLOB NOT NULL,body_size INT NOT NULL,body_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,body_truncated BOOLEAN NOT NULL,
 key_prefix VARBINARY(1024),key_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 id_header VARBINARY(1024),header_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 claimed_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,claimed_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 reason_code VARCHAR(64) NOT NULL,dlt_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 replay_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_quarantine_source(consumer_name,source_topic,source_partition,source_offset),
 CHECK(source_partition>=0 AND source_offset>=0 AND body_size>=0)
);
CREATE TABLE quarantine_replay_commands (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 quarantine_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_user_id BIGINT NOT NULL,target_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(1000) NOT NULL,outcome VARCHAR(40) NOT NULL,event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(quarantine_id) REFERENCES quarantined_messages(id),FOREIGN KEY(actor_user_id) REFERENCES users(id)
);

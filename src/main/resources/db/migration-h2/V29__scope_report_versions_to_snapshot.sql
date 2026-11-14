ALTER TABLE company_report_versions ADD COLUMN data_snapshot_id CHAR(36);
ALTER TABLE company_report_versions ADD CONSTRAINT fk_report_version_snapshot FOREIGN KEY(data_snapshot_id) REFERENCES data_snapshots(id);
CREATE INDEX idx_report_version_snapshot ON company_report_versions(data_snapshot_id,company_report_id,version_no);
CREATE TABLE snapshot_report_versions (
    snapshot_id CHAR(36) NOT NULL, company_id BIGINT NOT NULL, quarter_id BIGINT NOT NULL, report_version_id BIGINT NOT NULL,
    PRIMARY KEY(snapshot_id,company_id,quarter_id), UNIQUE(report_version_id),
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id), FOREIGN KEY(company_id) REFERENCES companies(id),
    FOREIGN KEY(quarter_id) REFERENCES quarters(id), FOREIGN KEY(report_version_id) REFERENCES company_report_versions(id)
);
CREATE TABLE active_data_snapshots (
    family VARCHAR(32) PRIMARY KEY, snapshot_id CHAR(36), generation BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(snapshot_id) REFERENCES data_snapshots(id)
);
INSERT INTO active_data_snapshots(family,snapshot_id) VALUES('FINANCIAL',NULL);

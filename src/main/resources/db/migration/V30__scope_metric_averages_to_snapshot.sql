ALTER TABLE metric_averages ADD COLUMN data_snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
ALTER TABLE metric_averages ADD CONSTRAINT fk_metric_average_snapshot FOREIGN KEY(data_snapshot_id) REFERENCES data_snapshots(id);

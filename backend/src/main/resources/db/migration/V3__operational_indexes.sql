CREATE INDEX idx_shipments_master_tracking ON shipments(master_tracking_number);
CREATE INDEX idx_shipments_status_created ON shipments(status, created_at);

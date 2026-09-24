CREATE TABLE accounts (
  id UUID PRIMARY KEY, company_name VARCHAR(200) NOT NULL, tax_number VARCHAR(100),
  billing_address TEXT, contact_name VARCHAR(200), email VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
  pricing_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE', pricing_value NUMERIC(12,4) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE shipments (
  id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES accounts(id), idempotency_key VARCHAR(200) NOT NULL,
  master_tracking_number VARCHAR(100), dhl_shipment_id VARCHAR(100), dhl_product_code VARCHAR(50) NOT NULL,
  ship_date DATE NOT NULL, sender JSONB NOT NULL, recipient JSONB NOT NULL, customs JSONB,
  dhl_estimated_cost NUMERIC(12,2), customer_price NUMERIC(12,2), currency CHAR(3) NOT NULL,
  status VARCHAR(30) NOT NULL, billing_status VARCHAR(20) NOT NULL DEFAULT 'UNBILLED', invoiced_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE(account_id, idempotency_key)
);
CREATE INDEX idx_shipments_account_created ON shipments(account_id, created_at DESC);
CREATE INDEX idx_shipments_billing ON shipments(billing_status, created_at);
CREATE TABLE shipment_packages (
  id UUID PRIMARY KEY, shipment_id UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
  package_index INTEGER NOT NULL, tracking_number VARCHAR(100), weight NUMERIC(10,3) NOT NULL,
  length NUMERIC(10,2) NOT NULL, width NUMERIC(10,2) NOT NULL, height NUMERIC(10,2) NOT NULL,
  UNIQUE(shipment_id, package_index)
);
CREATE TABLE shipment_labels (
  id UUID PRIMARY KEY, shipment_id UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
  package_id UUID REFERENCES shipment_packages(id), storage_key VARCHAR(500) NOT NULL,
  file_name VARCHAR(255) NOT NULL, content_type VARCHAR(100) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);

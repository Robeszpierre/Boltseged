CREATE TABLE pickup_bookings (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES accounts(id),
  dispatch_confirmation_numbers JSONB NOT NULL,
  warnings JSONB NOT NULL,
  pickup_date DATE NOT NULL,
  ready_time TIME NOT NULL,
  close_time TIME NOT NULL,
  pickup_address JSONB NOT NULL,
  package_count INTEGER NOT NULL,
  total_weight NUMERIC(12,3) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE pickup_booking_shipments (
  pickup_booking_id UUID NOT NULL REFERENCES pickup_bookings(id) ON DELETE CASCADE,
  shipment_id UUID NOT NULL REFERENCES shipments(id),
  PRIMARY KEY (pickup_booking_id, shipment_id)
);

CREATE INDEX idx_pickup_bookings_account_created
  ON pickup_bookings(account_id, created_at DESC);

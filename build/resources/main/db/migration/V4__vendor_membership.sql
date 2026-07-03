-- Add vendor_id column to membership table to link Vendor_Technician users to their vendor.
-- This allows a vendor to have multiple Vendor_Technician users linked via membership.
ALTER TABLE membership ADD COLUMN vendor_id UUID REFERENCES vendor(id);

-- Index for efficient lookup of vendor technicians by vendor
CREATE INDEX idx_membership_vendor_id ON membership(vendor_id);

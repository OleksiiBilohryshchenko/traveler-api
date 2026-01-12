-- =========================
-- Add JSONB columns for flexible data storage
-- =========================

-- Add metadata JSONB column to travel_plans
ALTER TABLE travel_plans
    ADD COLUMN metadata JSONB DEFAULT '{}'::jsonb NOT NULL;

-- Add attributes JSONB column to locations
ALTER TABLE locations
    ADD COLUMN attributes JSONB DEFAULT '{}'::jsonb NOT NULL;

-- =========================
-- Create GIN indexes for fast JSONB queries
-- =========================

-- Index for travel_plans metadata queries
-- Supports: WHERE metadata @> '{"tags": ["europe"]}'
CREATE INDEX idx_travel_plans_metadata_gin
    ON travel_plans USING GIN (metadata);

-- Index for locations attributes queries
-- Supports: WHERE attributes @> '{"category": "museum"}'
CREATE INDEX idx_locations_attributes_gin
    ON locations USING GIN (attributes);

-- =========================
-- Add specialized indexes for common query patterns
-- =========================

-- Index for searching by specific metadata keys
-- Supports: WHERE metadata->>'travel_style' = 'adventure'
CREATE INDEX idx_travel_plans_metadata_travel_style
    ON travel_plans ((metadata->>'travel_style'));

-- Index for searching by location category
-- Supports: WHERE attributes->>'category' = 'museum'
CREATE INDEX idx_locations_attributes_category
    ON locations ((attributes->>'category'));

-- Index for searching by location rating (as numeric)
-- Supports: WHERE (attributes->>'rating')::numeric >= 4.0
CREATE INDEX idx_locations_attributes_rating
    ON locations (((attributes->>'rating')::numeric));

-- =========================
-- Comments for documentation
-- =========================

COMMENT ON COLUMN travel_plans.metadata IS
'Flexible JSONB storage for: preferences (budget_category, travel_style, pace), participants array, tags array, custom user-defined fields';

COMMENT ON COLUMN locations.attributes IS
'Flexible JSONB storage for: rating, category, contact info (phone, website), business_hours object, accessibility array, tags array';

COMMENT ON INDEX idx_travel_plans_metadata_gin IS
'GIN index for containment queries (@>, ?, ?&, ?|) on travel_plans.metadata';

COMMENT ON INDEX idx_locations_attributes_gin IS
'GIN index for containment queries (@>, ?, ?&, ?|) on locations.attributes';
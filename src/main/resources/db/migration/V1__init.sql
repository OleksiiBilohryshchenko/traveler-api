-- =========================
-- Travel plans
-- =========================

CREATE TABLE travel_plans (
                              id UUID PRIMARY KEY,
                              title VARCHAR(200) NOT NULL,
                              description TEXT,
                              start_date DATE,
                              end_date DATE,
                              budget DECIMAL(10,2),
                              currency VARCHAR(3),
                              is_public BOOLEAN,
                              version INTEGER NOT NULL,
                              created_at TIMESTAMP,
                              updated_at TIMESTAMP,

                              CONSTRAINT check_dates
                                  CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),

                              CONSTRAINT check_budget
                                  CHECK (budget IS NULL OR budget >= 0)
);

-- =========================
-- Locations
-- =========================

CREATE TABLE locations (
                           id UUID PRIMARY KEY,
                           travel_plan_id UUID NOT NULL,
                           name VARCHAR(200) NOT NULL,
                           address TEXT,
                           latitude DECIMAL(10,6),
                           longitude DECIMAL(11,6),
                           visit_order INTEGER,
                           arrival_date TIMESTAMP,
                           departure_date TIMESTAMP,
                           budget DECIMAL(10,2),
                           notes TEXT,
                           created_at TIMESTAMP,

                           CONSTRAINT fk_location_plan
                               FOREIGN KEY (travel_plan_id)
                                   REFERENCES travel_plans(id)
                                   ON DELETE CASCADE,

                           CONSTRAINT check_coordinates_lat
                               CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),

                           CONSTRAINT check_coordinates_lng
                               CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),

                           CONSTRAINT check_location_dates
                               CHECK (departure_date IS NULL OR arrival_date IS NULL OR departure_date >= arrival_date),

                           CONSTRAINT check_location_budget
                               CHECK (budget IS NULL OR budget >= 0),

                           CONSTRAINT unique_plan_order
                               UNIQUE (travel_plan_id, visit_order)
);

CREATE INDEX idx_locations_plan_order
    ON locations(travel_plan_id, visit_order);

-- ============================================
-- Shard Registry Schema
-- Central storage for shard location mapping
-- ============================================

CREATE TABLE IF NOT EXISTS shard_registry (
                                              shard CHAR(1) PRIMARY KEY,         -- Shard key: 0-9, a-f
    node TEXT NOT NULL,                 -- Node name: postgres_00..postgres_03
    host TEXT NOT NULL,                 -- Host address
    port INT NOT NULL,                  -- Port number
    database TEXT NOT NULL,             -- Database name: db_0..db_f
    version INT NOT NULL DEFAULT 1,     -- Version for optimistic locking
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );

-- Index for version-based polling
CREATE INDEX IF NOT EXISTS idx_shard_registry_version ON shard_registry(version);

-- Trigger to auto-update timestamp
CREATE OR REPLACE FUNCTION update_shard_registry_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_shard_registry_timestamp ON shard_registry;
CREATE TRIGGER trg_shard_registry_timestamp
    BEFORE UPDATE ON shard_registry
    FOR EACH ROW
    EXECUTE FUNCTION update_shard_registry_timestamp();

-- ============================================
-- Initial data (default distribution)
-- Node 0: db_0..db_3, Node 1: db_4..db_7, etc.
-- ============================================

INSERT INTO shard_registry (shard, node, host, port, database, version) VALUES
                                                                            ('0', 'postgres_00', 'postgres_00', 5432, 'db_0', 1),
                                                                            ('1', 'postgres_00', 'postgres_00', 5432, 'db_1', 1),
                                                                            ('2', 'postgres_00', 'postgres_00', 5432, 'db_2', 1),
                                                                            ('3', 'postgres_00', 'postgres_00', 5432, 'db_3', 1),
                                                                            ('4', 'postgres_01', 'postgres_01', 5432, 'db_4', 1),
                                                                            ('5', 'postgres_01', 'postgres_01', 5432, 'db_5', 1),
                                                                            ('6', 'postgres_01', 'postgres_01', 5432, 'db_6', 1),
                                                                            ('7', 'postgres_01', 'postgres_01', 5432, 'db_7', 1),
                                                                            ('8', 'postgres_02', 'postgres_02', 5432, 'db_8', 1),
                                                                            ('9', 'postgres_02', 'postgres_02', 5432, 'db_9', 1),
                                                                            ('a', 'postgres_02', 'postgres_02', 5432, 'db_a', 1),
                                                                            ('b', 'postgres_02', 'postgres_02', 5432, 'db_b', 1),
                                                                            ('c', 'postgres_03', 'postgres_03', 5432, 'db_c', 1),
                                                                            ('d', 'postgres_03', 'postgres_03', 5432, 'db_d', 1),
                                                                            ('e', 'postgres_03', 'postgres_03', 5432, 'db_e', 1),
                                                                            ('f', 'postgres_03', 'postgres_03', 5432, 'db_f', 1)
    ON CONFLICT (shard) DO NOTHING;

-- ============================================
-- Utility views
-- ============================================

-- View: shards per node
CREATE OR REPLACE VIEW v_shards_per_node AS
SELECT
    node,
    COUNT(*) as shard_count,
    array_agg(shard ORDER BY shard) as shards,
    MAX(version) as max_version
FROM shard_registry
GROUP BY node
ORDER BY node;

-- View: global version (max across all shards)
CREATE OR REPLACE VIEW v_registry_version AS
SELECT MAX(version) as global_version, MAX(updated_at) as last_updated
FROM shard_registry;

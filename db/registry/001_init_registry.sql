CREATE TABLE shard_registry (
                                shard CHAR(1) PRIMARY KEY,
                                node TEXT NOT NULL,
                                host TEXT NOT NULL,
                                port INT NOT NULL,
                                database TEXT NOT NULL,
                                version INT NOT NULL
);

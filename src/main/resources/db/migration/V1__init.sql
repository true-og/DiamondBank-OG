CREATE TABLE diamond(
    uuid UUID PRIMARY KEY, bank_shards BIGINT DEFAULT 0, inventory_shards BIGINT DEFAULT 0, ender_chest_shards BIGINT DEFAULT 0,
    total_shards BIGINT GENERATED ALWAYS AS ( COALESCE(bank_shards, 0) + COALESCE(inventory_shards, 0) + COALESCE(ender_chest_shards, 0) ) STORED
);

CREATE INDEX idx_total_shards ON diamond(total_shards DESC);

CREATE TABLE diamond_log(
    id SERIAL PRIMARY KEY, player_uuid UUID NOT NULL, transferred_shards BIGINT NOT NULL, player_to_uuid UUID,
    transaction_reason TEXT, notes TEXT, timestamp TIMESTAMPTZ DEFAULT NOW()
);

CREATE FUNCTION fifo_limit_trigger() RETURNS TRIGGER AS $$
DECLARE
    max_rows CONSTANT BIGINT := 500;
BEGIN
    DELETE FROM diamond_log
    WHERE id <= (
        SELECT id FROM diamond_log
        ORDER BY id DESC
        OFFSET max_rows LIMIT 1
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fifo_limit_trigger
    AFTER INSERT ON diamond_log
    FOR EACH STATEMENT
    EXECUTE FUNCTION fifo_limit_trigger();
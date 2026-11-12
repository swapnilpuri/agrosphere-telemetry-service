-- Separate generated weather and soil devices can serve the same canonical block.
-- Device identity, publisher uniqueness and observation scope foreign keys remain enforced.
ALTER TABLE telemetry_device
    DROP CONSTRAINT telemetry_device_estate_id_block_id_key;

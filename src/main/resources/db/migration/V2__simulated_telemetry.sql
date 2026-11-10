-- Additive SIMULATED-only telemetry. V1 remains byte-preserved.
CREATE TABLE telemetry_device (
 device_id uuid PRIMARY KEY, estate_id uuid NOT NULL, block_id uuid NOT NULL,
 publisher_principal varchar(64) NOT NULL UNIQUE CHECK (publisher_principal ~ '^[A-Za-z0-9._-]{1,64}$'),
 active boolean NOT NULL, generated_run_id uuid NOT NULL,
 window_start timestamptz NOT NULL, window_end timestamptz NOT NULL,
 CHECK (window_start <= window_end), UNIQUE(device_id,estate_id,block_id), UNIQUE(estate_id,block_id)
);
CREATE TABLE telemetry_observation (
 observation_id uuid PRIMARY KEY, device_id uuid NOT NULL, estate_id uuid NOT NULL, block_id uuid NOT NULL,
 schema_version integer NOT NULL CHECK(schema_version=1),
 data_provenance varchar(16) NOT NULL CHECK(data_provenance='SIMULATED'),
 kind varchar(32) NOT NULL, unit varchar(16) NOT NULL, value numeric(18,6) NOT NULL,
 observed_at timestamptz NOT NULL, received_at timestamptz NOT NULL,
 interval_start timestamptz, interval_end timestamptz,
 FOREIGN KEY(device_id,estate_id,block_id) REFERENCES telemetry_device(device_id,estate_id,block_id),
 CHECK ((kind='AIR_TEMPERATURE' AND unit='CELSIUS' AND interval_start IS NULL AND interval_end IS NULL)
 OR (kind='SOIL_MOISTURE' AND unit='PERCENT' AND value BETWEEN 0 AND 100 AND interval_start IS NULL AND interval_end IS NULL)
 OR (kind='INTERVAL_RAINFALL' AND unit='MILLIMETRE' AND value>=0 AND interval_start IS NOT NULL AND interval_end IS NOT NULL AND interval_start<interval_end AND interval_end<=observed_at))
);
CREATE INDEX telemetry_latest ON telemetry_observation(estate_id,block_id,kind,observed_at DESC,((observation_id::text) COLLATE "C") DESC);
ALTER TABLE outbox_event ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT now();

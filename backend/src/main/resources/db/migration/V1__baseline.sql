CREATE TABLE app_metadata (
    id BIGSERIAL PRIMARY KEY,
    metadata_key VARCHAR(120) NOT NULL UNIQUE,
    metadata_value TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

INSERT INTO app_metadata (metadata_key, metadata_value)
VALUES ('schema_version', 'baseline');

CREATE TABLE model_pricing (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    input_price_per_million NUMERIC(12, 6) NOT NULL,
    output_price_per_million NUMERIC(12, 6) NOT NULL,
    source VARCHAR(16) NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    external_model_id VARCHAR(255),
    deprecated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_model_pricing_provider_model UNIQUE (provider, model),
    CONSTRAINT ck_model_pricing_input_nonneg CHECK (input_price_per_million >= 0),
    CONSTRAINT ck_model_pricing_output_nonneg CHECK (output_price_per_million >= 0),
    CONSTRAINT ck_model_pricing_source CHECK (source IN ('REMOTE', 'FALLBACK', 'OVERRIDE'))
);

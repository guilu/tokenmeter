CREATE TABLE cost_estimates (
    id BIGSERIAL PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    provider VARCHAR(60) NOT NULL,
    model VARCHAR(180) NOT NULL,
    mode VARCHAR(40) NOT NULL,
    base_tokens BIGINT NOT NULL,
    estimated_input_tokens BIGINT NOT NULL,
    estimated_output_tokens BIGINT NOT NULL,
    input_cost NUMERIC(20, 6) NOT NULL,
    output_cost NUMERIC(20, 6) NOT NULL,
    total_cost NUMERIC(20, 6) NOT NULL,
    formula TEXT NOT NULL,
    CONSTRAINT uq_cost_estimates_analysis_provider_model_mode UNIQUE (analysis_id, provider, model, mode)
);

CREATE INDEX idx_cost_estimates_analysis_id ON cost_estimates (analysis_id);
CREATE INDEX idx_cost_estimates_provider_model ON cost_estimates (provider, model);

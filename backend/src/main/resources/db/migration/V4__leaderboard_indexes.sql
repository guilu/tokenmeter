CREATE INDEX idx_cost_estimates_mode_provider_model_total_cost
    ON cost_estimates (mode, provider, model, total_cost);

CREATE INDEX idx_analysis_total_bytes ON analysis (total_bytes DESC);
CREATE INDEX idx_analysis_total_tokens ON analysis (total_tokens DESC);

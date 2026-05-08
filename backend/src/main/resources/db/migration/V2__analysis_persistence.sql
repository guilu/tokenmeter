CREATE TABLE analysis (
    id UUID PRIMARY KEY,
    repository_url VARCHAR(500) NOT NULL,
    clone_url VARCHAR(500) NOT NULL,
    owner_name VARCHAR(120) NOT NULL,
    repository_name VARCHAR(180) NOT NULL,
    status VARCHAR(40) NOT NULL,
    total_files BIGINT NOT NULL,
    total_lines BIGINT NOT NULL,
    total_bytes BIGINT NOT NULL,
    token_encoding VARCHAR(80) NOT NULL,
    total_tokens BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_analysis_repository_url ON analysis (repository_url);
CREATE INDEX idx_analysis_created_at ON analysis (created_at);

CREATE TABLE language_stats (
    id BIGSERIAL PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    language_name VARCHAR(120) NOT NULL,
    files BIGINT NOT NULL,
    lines BIGINT NOT NULL,
    bytes BIGINT NOT NULL,
    tokens BIGINT NOT NULL,
    CONSTRAINT uq_language_stats_analysis_language UNIQUE (analysis_id, language_name)
);

CREATE INDEX idx_language_stats_analysis_id ON language_stats (analysis_id);

CREATE TABLE analysis_job (
    id UUID PRIMARY KEY,
    repository_url VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    phase VARCHAR(24) NOT NULL,
    progress_percent SMALLINT NOT NULL,
    message TEXT,
    error_code VARCHAR(64),
    error_message TEXT,
    files_discovered BIGINT,
    files_processed BIGINT,
    files_skipped BIGINT,
    tokens_counted BIGINT,
    context_windows INTEGER,
    pricing_models_processed INTEGER,
    analysis_id UUID REFERENCES analysis (id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_analysis_job_status CHECK (status IN ('QUEUED','RUNNING','SUCCESS','FAILED')),
    CONSTRAINT chk_analysis_job_phase CHECK (phase IN ('QUEUED','CHECKING_CACHE','CLONING_REPOSITORY','SCANNING_FILES','FILTERING_FILES','COUNTING_TOKENS','CALCULATING_COSTS','SAVING_REPORT','COMPLETED','FAILED')),
    CONSTRAINT chk_analysis_job_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_analysis_job_progress_invariant CHECK (
        progress_percent <= 99 OR (status = 'SUCCESS' AND analysis_id IS NOT NULL)
    ),
    CONSTRAINT chk_analysis_job_files CHECK (
        files_processed IS NULL OR files_discovered IS NULL OR files_processed <= files_discovered
    ),
    CONSTRAINT chk_analysis_job_terminal_completed CHECK (
        (status IN ('SUCCESS','FAILED')) = (completed_at IS NOT NULL)
    ),
    CONSTRAINT chk_analysis_job_failed_payload CHECK (
        status <> 'FAILED' OR (error_code IS NOT NULL AND error_message IS NOT NULL)
    )
);

CREATE INDEX idx_analysis_job_status ON analysis_job (status);
CREATE INDEX idx_analysis_job_completed_at ON analysis_job (completed_at);
CREATE INDEX idx_analysis_job_created_at ON analysis_job (created_at);

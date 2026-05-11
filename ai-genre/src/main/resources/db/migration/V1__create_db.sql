CREATE TABLE uploads (
    file_hash CHAR(64) PRIMARY KEY,
    file_metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE files (
    file_hash CHAR(64) PRIMARY KEY REFERENCES uploads(file_hash),
    file_bytes BYTEA
);

CREATE TABLE audio_results (
    task_id UUID PRIMARY KEY,
    sample_hash CHAR(64) UNIQUE,
    file_hash CHAR(64) REFERENCES uploads(file_hash) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status in ('PENDING', 'PROCESSING', 'COMPLETE', 'FAILED')),
    error TEXT,
    result JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_uploads_created_at ON uploads(created_at);

CREATE INDEX idx_audio_results_sample_hash ON audio_results(sample_hash);
CREATE INDEX idx_audio_results_file_hash ON audio_results(file_hash);
CREATE INDEX idx_audio_results_status ON audio_results(status);
CREATE INDEX idx_audio_results_created_at ON audio_results(created_at);
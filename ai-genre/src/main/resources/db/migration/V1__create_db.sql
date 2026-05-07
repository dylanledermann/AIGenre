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
    sample_hash CHAR(64) PRIMARY KEY,
    file_hash CHAR(64) REFERENCES uploads(file_hash),
    task_id CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status in ('PENDING', 'PROCESSING', 'COMPLETE', 'FAILURE')),
    result JSONB,
    window_start_sample INT,
    sample_rate INT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE spectrograms (
    sample_hash CHAR(64) PRIMARY KEY REFERENCES audio_results(sample_hash),
    spectrogram_rows INT,
    spectrogram_cols INT,
    spectrogram BYTEA
);

CREATE INDEX idx_uploads_created_at ON uploads(created_at);
CREATE INDEX idx_audio_results_file_hash ON audio_results(file_hash);
CREATE INDEX idx_audio_results_status ON audio_results(status);
CREATE INDEX idx_audio_results_created_at ON audio_results(created_at);
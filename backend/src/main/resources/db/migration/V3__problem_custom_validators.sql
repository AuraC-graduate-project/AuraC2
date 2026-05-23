ALTER TABLE problems
    ADD COLUMN validation_mode VARCHAR(32) NOT NULL DEFAULT 'BUILTIN_COMPARE_POLICY',
    ADD COLUMN validator_language_id INTEGER,
    ADD COLUMN validator_source TEXT,
    ADD COLUMN validator_source_hash VARCHAR(64),
    ADD COLUMN validator_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN validator_created_at TIMESTAMP,
    ADD COLUMN validator_updated_at TIMESTAMP;

ALTER TABLE problems
    ADD CONSTRAINT chk_problems_validation_mode
        CHECK (validation_mode IN ('BUILTIN_COMPARE_POLICY', 'CUSTOM_VALIDATOR')),
    ADD CONSTRAINT chk_problems_validator_language_id
        CHECK (validator_language_id IS NULL OR validator_language_id > 0),
    ADD CONSTRAINT chk_problems_validator_hash
        CHECK (validator_source_hash IS NULL OR length(validator_source_hash) = 64),
    ADD CONSTRAINT chk_problems_validator_enabled_config
        CHECK (
            validator_enabled = FALSE
            OR (
                validation_mode = 'CUSTOM_VALIDATOR'
                AND validator_language_id IS NOT NULL
                AND validator_source IS NOT NULL
                AND validator_source_hash IS NOT NULL
            )
        );

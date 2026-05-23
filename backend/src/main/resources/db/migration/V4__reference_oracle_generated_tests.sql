CREATE TABLE reference_solutions (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    language_id INTEGER NOT NULL,
    source TEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_reference_solutions_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_reference_solutions_created_by
        FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT chk_reference_solutions_language
        CHECK (language_id > 0),
    CONSTRAINT chk_reference_solutions_hash
        CHECK (length(source_hash) = 64)
);

CREATE INDEX idx_reference_solutions_problem_active
    ON reference_solutions (problem_id, active);

CREATE TABLE input_generators (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    language_id INTEGER NOT NULL,
    source TEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    default_test_count INTEGER NOT NULL DEFAULT 10,
    created_by_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_input_generators_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_input_generators_created_by
        FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT chk_input_generators_language
        CHECK (language_id > 0),
    CONSTRAINT chk_input_generators_hash
        CHECK (length(source_hash) = 64),
    CONSTRAINT chk_input_generators_default_count
        CHECK (default_test_count BETWEEN 1 AND 100)
);

CREATE INDEX idx_input_generators_problem_active
    ON input_generators (problem_id, active);

CREATE TABLE input_validators (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    language_id INTEGER NOT NULL,
    source TEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_input_validators_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_input_validators_created_by
        FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT chk_input_validators_language
        CHECK (language_id > 0),
    CONSTRAINT chk_input_validators_hash
        CHECK (length(source_hash) = 64)
);

CREATE INDEX idx_input_validators_problem_active
    ON input_validators (problem_id, active);

CREATE TABLE generated_test_batches (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    seed BIGINT NOT NULL,
    generator_source_hash VARCHAR(64) NOT NULL,
    reference_solution_source_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_count INTEGER NOT NULL,
    generated_count INTEGER NOT NULL DEFAULT 0,
    invalid_count INTEGER NOT NULL DEFAULT 0,
    counterexample_count INTEGER NOT NULL DEFAULT 0,
    diagnostic VARCHAR(4096),
    created_by_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT fk_generated_test_batches_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_generated_test_batches_created_by
        FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT chk_generated_test_batches_status
        CHECK (status IN ('COMPLETED', 'FAILED')),
    CONSTRAINT chk_generated_test_batches_requested_count
        CHECK (requested_count BETWEEN 1 AND 100),
    CONSTRAINT chk_generated_test_batches_counts
        CHECK (generated_count >= 0 AND invalid_count >= 0 AND counterexample_count >= 0),
    CONSTRAINT chk_generated_test_batches_hashes
        CHECK (length(generator_source_hash) = 64 AND length(reference_solution_source_hash) = 64)
);

CREATE INDEX idx_generated_test_batches_problem_created
    ON generated_test_batches (problem_id, created_at);
CREATE INDEX idx_generated_test_batches_status
    ON generated_test_batches (status);

CREATE TABLE generated_test_cases (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    test_number INTEGER NOT NULL,
    seed BIGINT NOT NULL,
    input_data TEXT,
    reference_output TEXT,
    status VARCHAR(32) NOT NULL,
    promoted BOOLEAN NOT NULL DEFAULT FALSE,
    promoted_test_case_id BIGINT,
    diagnostic VARCHAR(4096),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_generated_test_cases_batch
        FOREIGN KEY (batch_id) REFERENCES generated_test_batches (id),
    CONSTRAINT fk_generated_test_cases_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_generated_test_cases_promoted_test_case
        FOREIGN KEY (promoted_test_case_id) REFERENCES test_cases (id),
    CONSTRAINT uk_generated_test_case_batch_number
        UNIQUE (batch_id, test_number),
    CONSTRAINT chk_generated_test_cases_status
        CHECK (status IN ('GENERATED', 'INVALID_INPUT', 'GENERATOR_FAILED', 'REFERENCE_FAILED')),
    CONSTRAINT chk_generated_test_cases_test_number
        CHECK (test_number > 0),
    CONSTRAINT chk_generated_test_cases_generated_payload
        CHECK (
            status <> 'GENERATED'
            OR (input_data IS NOT NULL AND reference_output IS NOT NULL)
        )
);

CREATE INDEX idx_generated_test_cases_problem
    ON generated_test_cases (problem_id);
CREATE INDEX idx_generated_test_cases_batch
    ON generated_test_cases (batch_id);
CREATE INDEX idx_generated_test_cases_promoted
    ON generated_test_cases (promoted);

CREATE TABLE counterexamples (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    generated_test_case_id BIGINT NOT NULL,
    judge_run_id BIGINT NOT NULL,
    generated_input TEXT NOT NULL,
    reference_output TEXT NOT NULL,
    team_output TEXT,
    verdict VARCHAR(32) NOT NULL,
    compare_policy VARCHAR(32),
    validation_mode VARCHAR(32),
    diagnostic VARCHAR(4096),
    promoted BOOLEAN NOT NULL DEFAULT FALSE,
    promoted_test_case_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_counterexamples_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_counterexamples_submission
        FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_counterexamples_generated_test_case
        FOREIGN KEY (generated_test_case_id) REFERENCES generated_test_cases (id),
    CONSTRAINT fk_counterexamples_promoted_test_case
        FOREIGN KEY (promoted_test_case_id) REFERENCES test_cases (id),
    CONSTRAINT chk_counterexamples_verdict
        CHECK (verdict IN ('ACCEPTED', 'WRONG_ANSWER', 'TLE', 'COMPILATION_ERROR', 'RUNTIME_ERROR', 'INTERNAL_ERROR', 'PENDING', 'PENDING_REJUDGE', 'RUNNING')),
    CONSTRAINT chk_counterexamples_compare_policy
        CHECK (compare_policy IS NULL OR compare_policy IN ('EXACT', 'NORMALIZED_TEXT', 'TOKEN_NORMALIZED', 'FLOAT_TOLERANCE')),
    CONSTRAINT chk_counterexamples_validation_mode
        CHECK (validation_mode IS NULL OR validation_mode IN ('BUILTIN_COMPARE_POLICY', 'CUSTOM_VALIDATOR'))
);

CREATE INDEX idx_counterexamples_problem_created
    ON counterexamples (problem_id, created_at);
CREATE INDEX idx_counterexamples_submission
    ON counterexamples (submission_id);
CREATE INDEX idx_counterexamples_promoted
    ON counterexamples (promoted);

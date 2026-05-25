ALTER TABLE generated_test_cases
    DROP CONSTRAINT IF EXISTS chk_generated_test_cases_status;

ALTER TABLE generated_test_cases
    ADD CONSTRAINT chk_generated_test_cases_status
        CHECK (status IN ('GENERATED', 'INVALID_INPUT', 'GENERATOR_FAILED', 'REFERENCE_FAILED', 'DUPLICATE'));

ALTER TABLE generated_test_cases
    DROP CONSTRAINT IF EXISTS chk_generated_test_cases_generated_payload;

ALTER TABLE generated_test_cases
    ADD CONSTRAINT chk_generated_test_cases_generated_payload
        CHECK (
            status <> 'GENERATED'
            OR (input_data IS NOT NULL AND reference_output IS NOT NULL)
        );

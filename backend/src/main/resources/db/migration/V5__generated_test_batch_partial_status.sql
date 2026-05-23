ALTER TABLE generated_test_batches
    DROP CONSTRAINT IF EXISTS chk_generated_test_batches_status;

ALTER TABLE generated_test_batches
    ADD CONSTRAINT chk_generated_test_batches_status
        CHECK (status IN ('COMPLETED', 'PARTIAL', 'FAILED'));

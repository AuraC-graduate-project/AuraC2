ALTER TABLE problems
    ADD COLUMN compare_policy VARCHAR(32) NOT NULL DEFAULT 'EXACT',
    ADD COLUMN float_absolute_epsilon DOUBLE PRECISION,
    ADD COLUMN float_relative_epsilon DOUBLE PRECISION;

ALTER TABLE problems
    ADD CONSTRAINT chk_problems_compare_policy
        CHECK (compare_policy IN ('EXACT', 'NORMALIZED_TEXT', 'TOKEN_NORMALIZED', 'FLOAT_TOLERANCE')),
    ADD CONSTRAINT chk_problems_float_absolute_epsilon
        CHECK (float_absolute_epsilon IS NULL OR float_absolute_epsilon >= 0),
    ADD CONSTRAINT chk_problems_float_relative_epsilon
        CHECK (float_relative_epsilon IS NULL OR float_relative_epsilon >= 0),
    ADD CONSTRAINT chk_problems_float_epsilon_policy
        CHECK (
            compare_policy = 'FLOAT_TOLERANCE'
            OR (float_absolute_epsilon IS NULL AND float_relative_epsilon IS NULL)
        ),
    ADD CONSTRAINT chk_problems_float_tolerance_has_epsilon
        CHECK (
            compare_policy <> 'FLOAT_TOLERANCE'
            OR COALESCE(float_absolute_epsilon, 0) > 0
            OR COALESCE(float_relative_epsilon, 0) > 0
        );

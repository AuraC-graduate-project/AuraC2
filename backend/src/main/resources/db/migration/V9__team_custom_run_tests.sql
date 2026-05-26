CREATE TABLE user_custom_test_cases (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    contest_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    input_data TEXT NOT NULL,
    expected_output TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_user_custom_tests_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_user_custom_tests_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id),
    CONSTRAINT fk_user_custom_tests_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_custom_tests_owner_problem
    ON user_custom_test_cases (owner_user_id, contest_id, problem_id);

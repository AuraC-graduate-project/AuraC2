-- Manual repair script for local schemas that were accidentally baselined at V1
-- before the baseline tables existed. On clean databases this is a no-op
-- because V1__baseline_schema.sql has already created the same objects.
--
-- This file is intentionally outside db/migration. Do not make it a Flyway
-- versioned migration, because adding a lower version after databases have
-- already reached V2/V3 makes Flyway validation fail with:
-- "Detected resolved migration not applied to database".

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    role VARCHAR(255) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_users_role_username ON users (role, username);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(255),
    device_ip VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked_expires
    ON refresh_tokens (user_id, revoked, expires_at);

CREATE TABLE IF NOT EXISTS contests (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    actual_start_time TIMESTAMP(6) WITH TIME ZONE,
    paused_at TIMESTAMP(6) WITH TIME ZONE,
    total_pause_millis BIGINT NOT NULL DEFAULT 0,
    description TEXT,
    status VARCHAR(255) NOT NULL,
    status_locked BOOLEAN NOT NULL DEFAULT FALSE,
    scoreboard_freeze_minutes INTEGER,
    penalty_minutes INTEGER NOT NULL DEFAULT 20
);

CREATE INDEX IF NOT EXISTS idx_contests_status_start_time ON contests (status, start_time);
CREATE INDEX IF NOT EXISTS idx_contests_start_time ON contests (start_time);

CREATE TABLE IF NOT EXISTS problems (
    id BIGSERIAL PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    time_limit INTEGER NOT NULL,
    memory_limit INTEGER NOT NULL,
    difficulty VARCHAR(255) NOT NULL,
    balloon_color VARCHAR(7) NOT NULL DEFAULT '#2563EB',
    CONSTRAINT fk_problems_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id)
);

CREATE INDEX IF NOT EXISTS idx_problems_contest ON problems (contest_id);

CREATE TABLE IF NOT EXISTS test_cases (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_test_cases_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
);

CREATE INDEX IF NOT EXISTS idx_test_cases_problem ON test_cases (problem_id);
CREATE INDEX IF NOT EXISTS idx_test_cases_problem_public ON test_cases (problem_id, is_public);

CREATE TABLE IF NOT EXISTS clarifications (
    id BIGSERIAL PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    problem_id BIGINT,
    user_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    standard_reply VARCHAR(255),
    reply TEXT,
    replied_at TIMESTAMP(6),
    replied_by_admin_id BIGINT,
    status VARCHAR(255) NOT NULL,
    reply_type VARCHAR(255),
    CONSTRAINT fk_clarifications_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id),
    CONSTRAINT fk_clarifications_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_clarifications_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_clarifications_replied_by_admin
        FOREIGN KEY (replied_by_admin_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_clarifications_contest_created
    ON clarifications (contest_id, created_at);
CREATE INDEX IF NOT EXISTS idx_clarifications_contest_reply_status_created
    ON clarifications (contest_id, reply_type, status, created_at);
CREATE INDEX IF NOT EXISTS idx_clarifications_user_contest_created
    ON clarifications (user_id, contest_id, created_at);
CREATE INDEX IF NOT EXISTS idx_clarifications_status_created
    ON clarifications (status, created_at);

CREATE TABLE IF NOT EXISTS submissions (
    id BIGSERIAL PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    language VARCHAR(255) NOT NULL,
    verdict VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    execution_time INTEGER,
    memory_usage INTEGER,
    judge_run_id BIGINT NOT NULL,
    CONSTRAINT fk_submissions_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id),
    CONSTRAINT fk_submissions_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT fk_submissions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_submissions_contest ON submissions (contest_id);
CREATE INDEX IF NOT EXISTS idx_submissions_problem ON submissions (problem_id);
CREATE INDEX IF NOT EXISTS idx_submissions_user ON submissions (user_id);
CREATE INDEX IF NOT EXISTS idx_submissions_verdict ON submissions (verdict);
CREATE INDEX IF NOT EXISTS idx_submissions_created_at ON submissions (created_at);
CREATE INDEX IF NOT EXISTS idx_submissions_contest_user ON submissions (contest_id, user_id);
CREATE INDEX IF NOT EXISTS idx_submissions_contest_problem ON submissions (contest_id, problem_id);
CREATE INDEX IF NOT EXISTS idx_submissions_contest_verdict ON submissions (contest_id, verdict);

CREATE TABLE IF NOT EXISTS submission_judge_results (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    judge_run_id BIGINT NOT NULL,
    test_case_number INTEGER NOT NULL,
    verdict VARCHAR(255) NOT NULL,
    execution_time INTEGER,
    memory_usage INTEGER,
    judge0_status_id INTEGER,
    judge0_status_description VARCHAR(128),
    diagnostic VARCHAR(4096),
    received_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_submission_judge_results_submission
        FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT uk_submission_judge_result_run_case
        UNIQUE (submission_id, judge_run_id, test_case_number)
);

CREATE INDEX IF NOT EXISTS idx_submission_judge_results_submission_run
    ON submission_judge_results (submission_id, judge_run_id);

CREATE TABLE IF NOT EXISTS scoreboard_reveal_states (
    id BIGSERIAL PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_scoreboard_reveal_states_contest UNIQUE (contest_id),
    CONSTRAINT fk_scoreboard_reveal_states_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id)
);

CREATE INDEX IF NOT EXISTS idx_scoreboard_reveal_states_status
    ON scoreboard_reveal_states (status);

CREATE TABLE IF NOT EXISTS scoreboard_reveal_cells (
    id BIGSERIAL PRIMARY KEY,
    reveal_state_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    reveal_order INTEGER NOT NULL,
    revealed BOOLEAN NOT NULL,
    revealed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT fk_scoreboard_reveal_cells_state
        FOREIGN KEY (reveal_state_id) REFERENCES scoreboard_reveal_states (id),
    CONSTRAINT fk_scoreboard_reveal_cells_team
        FOREIGN KEY (team_id) REFERENCES users (id),
    CONSTRAINT fk_scoreboard_reveal_cells_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id),
    CONSTRAINT uk_scoreboard_reveal_state_team_problem
        UNIQUE (reveal_state_id, team_id, problem_id)
);

CREATE INDEX IF NOT EXISTS idx_scoreboard_reveal_cells_state_order
    ON scoreboard_reveal_cells (reveal_state_id, reveal_order, id);
CREATE INDEX IF NOT EXISTS idx_scoreboard_reveal_cells_state_revealed_order
    ON scoreboard_reveal_cells (reveal_state_id, revealed, reveal_order, id);

-- Repair migration for development/long-lived databases whose Flyway history
-- predates the clarifications table now present in V1__baseline_schema.sql.
-- Fresh databases already get this table from V1; these IF NOT EXISTS clauses
-- keep the migration forward-only and harmless there.

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
    reply_type VARCHAR(255)
);

ALTER TABLE IF EXISTS clarifications
    ADD COLUMN IF NOT EXISTS contest_id BIGINT,
    ADD COLUMN IF NOT EXISTS problem_id BIGINT,
    ADD COLUMN IF NOT EXISTS user_id BIGINT,
    ADD COLUMN IF NOT EXISTS question TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6),
    ADD COLUMN IF NOT EXISTS standard_reply VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reply TEXT,
    ADD COLUMN IF NOT EXISTS replied_at TIMESTAMP(6),
    ADD COLUMN IF NOT EXISTS replied_by_admin_id BIGINT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reply_type VARCHAR(255);

UPDATE clarifications
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

UPDATE clarifications
SET status = 'PENDING'
WHERE status IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_clarifications_contest'
          AND conrelid = 'clarifications'::regclass
    ) THEN
        ALTER TABLE clarifications
            ADD CONSTRAINT fk_clarifications_contest
            FOREIGN KEY (contest_id) REFERENCES contests (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_clarifications_problem'
          AND conrelid = 'clarifications'::regclass
    ) THEN
        ALTER TABLE clarifications
            ADD CONSTRAINT fk_clarifications_problem
            FOREIGN KEY (problem_id) REFERENCES problems (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_clarifications_user'
          AND conrelid = 'clarifications'::regclass
    ) THEN
        ALTER TABLE clarifications
            ADD CONSTRAINT fk_clarifications_user
            FOREIGN KEY (user_id) REFERENCES users (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_clarifications_replied_by_admin'
          AND conrelid = 'clarifications'::regclass
    ) THEN
        ALTER TABLE clarifications
            ADD CONSTRAINT fk_clarifications_replied_by_admin
            FOREIGN KEY (replied_by_admin_id) REFERENCES users (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_clarifications_contest_created
    ON clarifications (contest_id, created_at);

CREATE INDEX IF NOT EXISTS idx_clarifications_contest_reply_status_created
    ON clarifications (contest_id, reply_type, status, created_at);

CREATE INDEX IF NOT EXISTS idx_clarifications_user_contest_created
    ON clarifications (user_id, contest_id, created_at);

CREATE INDEX IF NOT EXISTS idx_clarifications_status_created
    ON clarifications (status, created_at);

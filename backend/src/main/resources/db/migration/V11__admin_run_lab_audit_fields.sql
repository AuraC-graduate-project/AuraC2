ALTER TABLE contest_moderation_audit_logs
    ALTER COLUMN team_id DROP NOT NULL;

ALTER TABLE contest_moderation_audit_logs
    ADD COLUMN problem_id BIGINT,
    ADD COLUMN language_id INTEGER,
    ADD COLUMN source_hash VARCHAR(64),
    ADD COLUMN execution_mode VARCHAR(64);

ALTER TABLE contest_moderation_audit_logs
    ADD CONSTRAINT fk_contest_moderation_audit_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id);

ALTER TABLE contest_moderation_audit_logs
    DROP CONSTRAINT chk_contest_moderation_audit_action;

ALTER TABLE contest_moderation_audit_logs
    ADD CONSTRAINT chk_contest_moderation_audit_action
        CHECK (action_type IN (
            'HIDE_FROM_SCOREBOARD',
            'SHOW_ON_SCOREBOARD',
            'DISQUALIFY_TEAM',
            'RESTORE_TEAM',
            'DISABLE_SUBMIT',
            'ENABLE_SUBMIT',
            'DISABLE_RUN',
            'ENABLE_RUN',
            'ADMIN_RUN_LAB_EXECUTION'
        ));

CREATE INDEX idx_moderation_audit_problem_created
    ON contest_moderation_audit_logs (problem_id, created_at);

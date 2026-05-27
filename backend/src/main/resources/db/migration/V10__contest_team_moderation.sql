CREATE TABLE contest_team_moderations (
    id BIGSERIAL PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    hidden_from_scoreboard BOOLEAN NOT NULL DEFAULT FALSE,
    submit_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    run_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reason TEXT,
    updated_by_admin_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_contest_team_moderations_contest_team
        UNIQUE (contest_id, team_id),
    CONSTRAINT fk_contest_team_moderations_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id),
    CONSTRAINT fk_contest_team_moderations_team
        FOREIGN KEY (team_id) REFERENCES users (id),
    CONSTRAINT fk_contest_team_moderations_updated_by_admin
        FOREIGN KEY (updated_by_admin_id) REFERENCES users (id),
    CONSTRAINT chk_contest_team_moderations_status
        CHECK (status IN ('ACTIVE', 'DISQUALIFIED'))
);

CREATE INDEX idx_contest_team_moderations_contest
    ON contest_team_moderations (contest_id);
CREATE INDEX idx_contest_team_moderations_team
    ON contest_team_moderations (team_id);
CREATE INDEX idx_contest_team_moderations_scoreboard
    ON contest_team_moderations (contest_id, hidden_from_scoreboard, status);

CREATE TABLE contest_moderation_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    reason TEXT,
    old_value_json TEXT NOT NULL,
    new_value_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_contest_moderation_audit_contest
        FOREIGN KEY (contest_id) REFERENCES contests (id),
    CONSTRAINT fk_contest_moderation_audit_team
        FOREIGN KEY (team_id) REFERENCES users (id),
    CONSTRAINT fk_contest_moderation_audit_admin
        FOREIGN KEY (admin_id) REFERENCES users (id),
    CONSTRAINT chk_contest_moderation_audit_action
        CHECK (action_type IN (
            'HIDE_FROM_SCOREBOARD',
            'SHOW_ON_SCOREBOARD',
            'DISQUALIFY_TEAM',
            'RESTORE_TEAM',
            'DISABLE_SUBMIT',
            'ENABLE_SUBMIT',
            'DISABLE_RUN',
            'ENABLE_RUN'
        ))
);

CREATE INDEX idx_moderation_audit_contest_created
    ON contest_moderation_audit_logs (contest_id, created_at);
CREATE INDEX idx_moderation_audit_team_created
    ON contest_moderation_audit_logs (team_id, created_at);
CREATE INDEX idx_moderation_audit_admin_created
    ON contest_moderation_audit_logs (admin_id, created_at);
CREATE INDEX idx_moderation_audit_action_created
    ON contest_moderation_audit_logs (action_type, created_at);

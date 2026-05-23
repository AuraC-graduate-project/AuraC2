package com.server.contestControl.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationDefinitionTest {

    @Test
    void defaultConfigurationUsesFlywayAndDoesNotDefaultToCreateDrop() throws IOException {
        String applicationYml = readProjectFile("src/main/resources/application.yml");
        String testApplicationYml = readProjectFile("src/test/resources/application.yml");

        assertThat(applicationYml).contains("ddl-auto: ${SPRING_JPA_DDL_AUTO:validate}");
        assertThat(applicationYml).contains("flyway:");
        assertThat(applicationYml).contains("enabled: ${SPRING_FLYWAY_ENABLED:true}");
        assertThat(applicationYml).contains("baseline-version: ${SPRING_FLYWAY_BASELINE_VERSION:0}");
        assertThat(applicationYml).doesNotContain("SPRING_JPA_DDL_AUTO:create-drop");

        assertThat(testApplicationYml).contains("ddl-auto: create-drop");
        assertThat(testApplicationYml).contains("enabled: false");
    }

    @Test
    void baselineMigrationPreservesUniqueConstraintsAndAddsCoreIndexes() throws IOException {
        String migration = readProjectFile("src/main/resources/db/migration/V1__baseline_schema.sql");

        assertThat(migration).contains("CONSTRAINT uk_users_username UNIQUE (username)");
        assertThat(migration).contains("CONSTRAINT uk_submission_judge_result_run_case");
        assertThat(migration).contains("UNIQUE (submission_id, judge_run_id, test_case_number)");
        assertThat(migration).contains("CONSTRAINT uk_scoreboard_reveal_states_contest UNIQUE (contest_id)");
        assertThat(migration).contains("CONSTRAINT uk_scoreboard_reveal_state_team_problem");
        assertThat(migration).contains("UNIQUE (reveal_state_id, team_id, problem_id)");

        assertThat(migration).contains("CREATE INDEX idx_submissions_contest_user");
        assertThat(migration).contains("CREATE INDEX idx_submission_judge_results_submission_run");
        assertThat(migration).contains("CREATE INDEX idx_test_cases_problem_public");
        assertThat(migration).contains("CREATE INDEX idx_refresh_tokens_user_revoked_expires");
        assertThat(migration).contains("CREATE INDEX idx_scoreboard_reveal_cells_state_revealed_order");
    }

    @Test
    void baselineMigrationHardensHiddenTestCasePersistenceColumns() throws IOException {
        String migration = readProjectFile("src/main/resources/db/migration/V1__baseline_schema.sql");

        assertThat(migration).contains("input_data TEXT NOT NULL");
        assertThat(migration).contains("expected_output TEXT NOT NULL");
        assertThat(migration).contains("is_public BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(migration).contains("CONSTRAINT fk_test_cases_problem");
    }

    @Test
    void comparePolicyMigrationAddsProblemPolicyColumnsAndConstraints() throws IOException {
        String migration = readProjectFile("src/main/resources/db/migration/V2__problem_compare_policy.sql");

        assertThat(migration).contains("compare_policy VARCHAR(32) NOT NULL DEFAULT 'EXACT'");
        assertThat(migration).contains("float_absolute_epsilon DOUBLE PRECISION");
        assertThat(migration).contains("float_relative_epsilon DOUBLE PRECISION");
        assertThat(migration).contains("chk_problems_compare_policy");
        assertThat(migration).contains("'NORMALIZED_TEXT'");
        assertThat(migration).contains("'TOKEN_NORMALIZED'");
        assertThat(migration).contains("'FLOAT_TOLERANCE'");
        assertThat(migration).contains("chk_problems_float_tolerance_has_epsilon");
    }

    @Test
    void officialMigrationsRemainStrictlyForwardOnly() throws IOException {
        Path migrationDir = projectPath("src/main/resources/db/migration");
        List<String> migrationNames;
        try (var stream = Files.list(migrationDir)) {
            migrationNames = stream
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertThat(migrationNames).containsExactly(
                "V1__baseline_schema.sql",
                "V2__problem_compare_policy.sql",
                "V3__problem_custom_validators.sql"
        );
        assertThat(migrationNames).noneMatch(name -> name.startsWith("V1_1"));
    }

    @Test
    void customValidatorMigrationAddsProblemValidatorColumnsAndConstraints() throws IOException {
        String migration = readProjectFile("src/main/resources/db/migration/V3__problem_custom_validators.sql");

        assertThat(migration).contains("validation_mode VARCHAR(32) NOT NULL DEFAULT 'BUILTIN_COMPARE_POLICY'");
        assertThat(migration).contains("validator_language_id INTEGER");
        assertThat(migration).contains("validator_source TEXT");
        assertThat(migration).contains("validator_source_hash VARCHAR(64)");
        assertThat(migration).contains("validator_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(migration).contains("chk_problems_validation_mode");
        assertThat(migration).contains("'CUSTOM_VALIDATOR'");
        assertThat(migration).contains("chk_problems_validator_enabled_config");
    }

    @Test
    void manualBaselineRepairScriptIsOutsideFlywayManagedMigrationPath() throws IOException {
        String script = readProjectFile("src/main/resources/db/manual/repair_baseline_schema_for_dev.sql");

        assertThat(script).contains("intentionally outside db/migration");
        assertThat(script).contains("CREATE TABLE IF NOT EXISTS problems");
        assertThat(script).contains("CREATE TABLE IF NOT EXISTS submissions");
    }

    private String readProjectFile(String relativePath) throws IOException {
        return Files.readString(projectPath(relativePath));
    }

    private Path projectPath(String relativePath) throws IOException {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }

        Path nestedBackend = cwd.resolve("backend").resolve(relativePath);
        if (Files.exists(nestedBackend)) {
            return nestedBackend;
        }

        throw new IOException("Cannot find " + relativePath + " from " + cwd);
    }
}

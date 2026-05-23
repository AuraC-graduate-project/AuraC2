package com.server.contestControl.persistence;

import com.server.contestControl.AuraServerApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = AuraServerApplication.class,
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.flyway.create-schemas=true",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
class MigrationManagedSchemaStartupTest {

    private static final String SCHEMA = "aurac_migration_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MigrationManagedSchemaStartupTest::schemaJdbcUrl);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("bootstrap.admin.credentials-file", () ->
                Path.of(System.getProperty("java.io.tmpdir"), SCHEMA + "-admin-account.txt").toString()
        );
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection = DriverManager.getConnection(baseJdbcUrl(), username(), password());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void cleanPostgresSchemaRunsFlywayMigrationsAndHibernateValidate() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class
        );

        assertThat(versions).containsSequence("1", "2", "3", "4", "5");
        assertThat(tableExists("problems")).isTrue();
        assertThat(tableExists("reference_solutions")).isTrue();
        assertThat(tableExists("generated_test_batches")).isTrue();
        assertThat(tableExists("counterexamples")).isTrue();
        assertThat(columnExists("problems", "compare_policy")).isTrue();
        assertThat(columnExists("problems", "validation_mode")).isTrue();
        assertThat(columnExists("problems", "validator_source_hash")).isTrue();
    }

    @Test
    void existingDatabaseAlreadyMigratedThroughVersionThreeValidatesWithoutRetroactiveMigrations() throws Exception {
        String existingSchema = "aurac_existing_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(baseJdbcUrl(), username(), password())
                    .schemas(existingSchema)
                    .defaultSchema(existingSchema)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .target("3")
                    .load();
            flyway.migrate();

            Flyway.configure()
                    .dataSource(baseJdbcUrl(), username(), password())
                    .schemas(existingSchema)
                    .defaultSchema(existingSchema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            Flyway.configure()
                    .dataSource(baseJdbcUrl(), username(), password())
                    .schemas(existingSchema)
                    .defaultSchema(existingSchema)
                    .locations("classpath:db/migration")
                    .load()
                    .validate();
        } finally {
            dropSchema(existingSchema);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM information_schema.tables
                        WHERE table_schema = ? AND table_name = ?
                        """,
                Integer.class,
                SCHEMA,
                tableName
        );
        return count != null && count == 1;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = ? AND column_name = ?
                        """,
                Integer.class,
                SCHEMA,
                tableName,
                columnName
        );
        return count != null && count == 1;
    }

    private static String schemaJdbcUrl() {
        String separator = baseJdbcUrl().contains("?") ? "&" : "?";
        return baseJdbcUrl() + separator + "currentSchema=" + SCHEMA;
    }

    private static String baseJdbcUrl() {
        return System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/authserver");
    }

    private static String username() {
        return System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres");
    }

    private static String password() {
        return System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "1234");
    }

    private static void dropSchema(String schema) throws Exception {
        try (var connection = DriverManager.getConnection(baseJdbcUrl(), username(), password());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}

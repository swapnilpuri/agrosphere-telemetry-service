package com.agrosphere.telemetry;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

class RegistryMigrationIT {
    static PostgreSQLContainer postgres;
    static final String run = UUID.randomUUID().toString();

    @BeforeAll
    static void start() {
        postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("registrybase")
            .withLabel("agrosphere.poc-close.migration.run", run)
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(5432))));
        postgres.start();
    }

    DriverManagerDataSource database() {
        String name = "registry_" + UUID.randomUUID().toString().replace("-", "");
        var base = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(base).execute("CREATE DATABASE " + name);
        return new DriverManagerDataSource(postgres.getJdbcUrl().replace("/registrybase", "/" + name),
            postgres.getUsername(), postgres.getPassword());
    }

    void register(JdbcTemplate jdbc, UUID device, UUID estate, UUID block, String publisher) {
        jdbc.update("INSERT INTO telemetry_device VALUES(?,?,?,?,true,?,now()-interval '1 hour',now()+interval '1 hour')",
            device, estate, block, publisher, UUID.fromString(run));
    }

    String snapshot(JdbcTemplate jdbc, UUID device, UUID observation, UUID event) {
        return jdbc.queryForObject("SELECT md5(row_to_json(d)::text) FROM telemetry_device d WHERE device_id=?", String.class, device)
            + jdbc.queryForObject("SELECT md5(row_to_json(o)::text) FROM telemetry_observation o WHERE observation_id=?", String.class, observation)
            + jdbc.queryForObject("SELECT md5(row_to_json(e)::text) FROM outbox_event e WHERE event_id=?", String.class, event);
    }

    @Test
    void freshDatabaseAllowsSeparateWeatherAndSoilDevicesOnOneBlock() {
        var datasource = database();
        Flyway.configure().dataSource(datasource).load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        UUID estate = UUID.randomUUID(), block = UUID.randomUUID();
        register(jdbc, UUID.randomUUID(), estate, block, "weather");
        register(jdbc, UUID.randomUUID(), estate, block, "soil");
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM telemetry_device WHERE estate_id=? AND block_id=?", Integer.class, estate, block));
    }

    @Test
    void populatedV2UpgradePreservesRowsAndIdentityGuards() {
        var datasource = database();
        Flyway.configure().dataSource(datasource).target("2").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        UUID estate = UUID.randomUUID(), block = UUID.randomUUID(), weather = UUID.randomUUID();
        UUID observation = UUID.randomUUID(), event = UUID.randomUUID();
        register(jdbc, weather, estate, block, "weather");
        jdbc.update("INSERT INTO telemetry_observation VALUES(?,?,?,?,1,'SIMULATED','AIR_TEMPERATURE','CELSIUS',27.5,now(),now(),NULL,NULL)", observation, weather, estate, block);
        jdbc.update("INSERT INTO outbox_event(event_id,aggregate_id,event_type,event_version,correlation_id,occurred_at,payload) VALUES(?,?,'TelemetryObservationRecorded',1,?,now(),'{}')", event, observation, UUID.randomUUID());
        String before = snapshot(jdbc, weather, observation, event);
        UUID soil = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> register(jdbc, soil, estate, block, "soil"));
        Flyway.configure().dataSource(datasource).load().migrate();
        register(jdbc, soil, estate, block, "soil");
        assertEquals(before, snapshot(jdbc, weather, observation, event));
        assertThrows(DataIntegrityViolationException.class, () -> register(jdbc, weather, estate, UUID.randomUUID(), "duplicate-id"));
        assertThrows(DataIntegrityViolationException.class, () -> register(jdbc, UUID.randomUUID(), estate, UUID.randomUUID(), "weather"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO telemetry_observation VALUES(?,?,?,?,1,'SIMULATED','AIR_TEMPERATURE','CELSIUS',27.5,now(),now(),NULL,NULL)",
            UUID.randomUUID(), soil, estate, UUID.randomUUID()));
        Flyway.configure().dataSource(datasource).load().validate();
    }

    @Test
    void failedUpgradeTransactionRestoresConstraintAndExistingRegistry() throws Exception {
        var datasource = database();
        Flyway.configure().dataSource(datasource).target("2").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        UUID estate = UUID.randomUUID(), block = UUID.randomUUID(), device = UUID.randomUUID();
        register(jdbc, device, estate, block, "weather");
        String before = jdbc.queryForObject("SELECT md5(row_to_json(d)::text) FROM telemetry_device d", String.class);
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V3__multiple_devices_per_block.sql"));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            jdbc.execute(sql);
            throw new IllegalStateException("Generated rollback probe");
        }));
        assertEquals(before, jdbc.queryForObject("SELECT md5(row_to_json(d)::text) FROM telemetry_device d", String.class));
        assertThrows(DataIntegrityViolationException.class, () -> register(jdbc, UUID.randomUUID(), estate, block, "soil"));
    }

    @AfterAll
    static void stop() throws Exception {
        if (postgres == null) return;
        String id = postgres.getContainerId();
        postgres.close();
        Path evidence = Path.of(System.getProperty("telemetry.test.evidence.directory", "target/poc-close-registry-migration"));
        Files.createDirectories(evidence);
        Files.writeString(evidence.resolve("registry-owned.json"), "{\"runId\":\"" + run + "\",\"containerId\":\"" + id + "\",\"closeCompleted\":true}");
    }
}

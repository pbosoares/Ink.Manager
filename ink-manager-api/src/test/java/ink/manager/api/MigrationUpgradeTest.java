package ink.manager.api;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="INK_TEST_DB_DRIVER", matches="org.postgresql.Driver")
class MigrationUpgradeTest {
    @Test
    void preservaIdsEVinculosDoBancoLegado() throws Exception {
        String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("INK_TEST_DB_URL");
        String user = System.getenv("INK_TEST_DB_USER");
        String password = System.getenv().getOrDefault("INK_TEST_DB_PASSWORD", "");
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                String initial;
                try (var stream = getClass().getResourceAsStream("/db/migration/V1__initial_schema.sql")) {
                    initial = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        .replace("EXISTS cliente (", "EXISTS clientes (")
                        .replace("REFERENCES cliente(", "REFERENCES clientes(");
                }
                for (String sql : initial.split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
                statement.execute("INSERT INTO clientes(id,nome,telefone) VALUES (42,'Cliente legado','21999999999')");
                statement.execute("INSERT INTO marcacoes(id,cliente_id,data,horario,status) VALUES (7,42,'2099-01-01','10:00','AGENDADA')");
                Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration","classpath:db/vendor/postgresql")
                    .baselineOnMigrate(true).baselineVersion("0").load().migrate();
                try (ResultSet result = statement.executeQuery("SELECT c.id,m.cliente_id,m.duracao_minutos,m.version FROM cliente c JOIN marcacoes m ON m.cliente_id=c.id")) {
                    assertTrue(result.next());
                    assertEquals(42,result.getLong(1));
                    assertEquals(42,result.getLong(2));
                    assertEquals(60,result.getInt(3));
                    assertEquals(0,result.getLong(4));
                    assertFalse(result.next());
                }
            } finally {
                // The schema name is generated locally and contains only [a-z0-9_].
                connection.setSchema("public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}

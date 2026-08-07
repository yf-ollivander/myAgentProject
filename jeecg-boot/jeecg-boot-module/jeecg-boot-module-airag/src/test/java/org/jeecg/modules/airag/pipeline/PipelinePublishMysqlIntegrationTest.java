package org.jeecg.modules.airag.pipeline;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PipelinePublishMysqlIntegrationTest {
    @Test
    void mysqlUniqueTriggerKeyAllowsOnlyOneConcurrentAlias() throws Exception {
        String url = System.getenv("PIPELINE_TEST_MYSQL_URL");
        assumeTrue(url != null && !url.isBlank(), "PIPELINE_TEST_MYSQL_URL is not configured");
        String user = System.getenv().getOrDefault("PIPELINE_TEST_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("PIPELINE_TEST_MYSQL_PASSWORD", "");
        String table = "ai_pipeline_trigger_key_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + table + " (id varchar(36) PRIMARY KEY, tenant_id varchar(32) NOT NULL, normalized_value varchar(100) COLLATE utf8mb4_bin NOT NULL, UNIQUE KEY uk_tenant_value(tenant_id,normalized_value)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> insert = () -> {
                try (var connection = DriverManager.getConnection(url, user, password);
                     var statement = connection.prepareStatement("INSERT INTO " + table + " VALUES (?,?,?)")) {
                    statement.setString(1, UUID.randomUUID().toString()); statement.setString(2, "0"); statement.setString(3, "alias");
                    statement.executeUpdate(); return 1;
                } catch (Exception duplicate) { return 0; }
            };
            int successful = pool.invokeAll(java.util.List.of(insert, insert)).stream().mapToInt(future -> {
                try { return future.get(); } catch (Exception e) { return 0; }
            }).sum();
            assertEquals(1, successful);
        } finally {
            pool.shutdownNow();
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                statement.execute("DROP TABLE " + table);
            }
        }
    }
}

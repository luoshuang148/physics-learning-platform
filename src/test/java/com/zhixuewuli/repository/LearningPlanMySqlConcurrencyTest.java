package com.zhixuewuli.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class LearningPlanMySqlConcurrencyTest {
    static String url, user, password;

    @BeforeAll static void migrate() {
        url = System.getenv("MYSQL_TEST_URL"); user = System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
        password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        assertNotNull(url, "Set MYSQL_TEST_URL to an isolated test schema; no fallback to application database");
        // Match application.yml: the CI upgrade fixture leaves a non-empty V7 schema
        // without flyway_schema_history; baseline at 8 then apply V9+.
        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("8")
                .load()
                .migrate();
    }

    @Test void twoConcurrentActivationsHaveOneVersionIncrement() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        long userId = 0, eventId = 0, planId = 0;
        try (var connection = DriverManager.getConnection(url, user, password)) {
            userId = insert(connection, "INSERT INTO users(username,password_hash,user_type,real_name,active) VALUES(?, 'test-hash','STUDENT',?,TRUE)", "plan_" + suffix, "Plan " + suffix);
            eventId = insert(connection, "INSERT INTO teacher_events(teacher_id,teacher_name,event_title,event_timestamp,status) VALUES(?, 'fixture','plan fixture',NOW(),'PUBLISHED')", userId);
            execute(connection, "INSERT INTO learning_plan_previews(run_id,user_id,event_id,source_answer_revision,plan_json,status,expires_at) VALUES(?,?,?,'revision','{}','SUCCEEDED',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY))", runId,userId,eventId);
            planId = insert(connection, "INSERT INTO learning_plans(user_id,event_id,source_run_id,request_id,source_answer_revision,days,status,version) VALUES(?,?,?,?,'revision',2,'DRAFT',1)", userId,eventId,runId,requestId);
        }
        final long activatedPlan = planId;
        final long ownerId = userId;
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            var first = pool.submit(() -> activate(activatedPlan, ownerId, ready, start));
            var second = pool.submit(() -> activate(activatedPlan, ownerId, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            int wins = first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
            assertEquals(1, wins, "Only one conditional activation should change DRAFT to ACTIVE");
            try (var connection = DriverManager.getConnection(url,user,password);
                 var statement = connection.prepareStatement("SELECT status,version FROM learning_plans WHERE id=?")) {
                statement.setLong(1, planId);
                try (var rows = statement.executeQuery()) { assertTrue(rows.next()); assertEquals("ACTIVE", rows.getString(1)); assertEquals(2L, rows.getLong(2)); }
            }
        } finally {
            try (var connection = DriverManager.getConnection(url,user,password); var statement = connection.createStatement()) {
                if (planId != 0) statement.executeUpdate("DELETE FROM learning_plans WHERE id=" + planId);
                statement.executeUpdate("DELETE FROM learning_plan_previews WHERE run_id='" + runId + "'");
                if (eventId != 0) statement.executeUpdate("DELETE FROM teacher_events WHERE id=" + eventId);
                if (userId != 0) statement.executeUpdate("DELETE FROM users WHERE id=" + userId);
            }
        }
    }

    @Test void migrationCreatesPlanTablesWithIdempotencyConstraints() throws Exception {
        try (var connection = DriverManager.getConnection(url,user,password);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('learning_plan_previews','learning_plans','learning_plan_items')")) {
            assertTrue(rows.next()); assertEquals(3, rows.getInt(1));
        }
        try (var connection = DriverManager.getConnection(url,user,password);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='learning_plans' AND index_name IN ('uk_learning_plan_request','uk_learning_plan_run')")) {
            assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
        }
    }

    private int activate(long planId, long userId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); start.await();
        try (var connection = DriverManager.getConnection(url,user,password);
             var statement = connection.prepareStatement("UPDATE learning_plans SET status='ACTIVE',version=version+1,activated_at=UTC_TIMESTAMP(6) WHERE id=? AND user_id=? AND status='DRAFT' AND version=1")) {
            statement.setLong(1,planId); statement.setLong(2,userId); return statement.executeUpdate();
        }
    }

    private long insert(java.sql.Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); return keys.getLong(1); }
        }
    }

    private void execute(java.sql.Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); statement.executeUpdate();
        }
    }
}

package org.exaspace.log4jq;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;
import org.exaspace.log4jq.support.Dialects;
import org.exaspace.log4jq.support.JdbcHelper;
import org.exaspace.log4jq.support.Log4jSupport;
import org.exaspace.log4jq.support.LogMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class DiscardingJdbcAppenderIntegrationTest {

    private final Dialects.Dialect sql = Dialects.forDefaultDatabase();
    private JdbcHelper db;
    private final int reconnectTimeMillis = 500;
    private final Properties jdbcProps = new Properties();

    {
        jdbcProps.put("url", sql.url());
        jdbcProps.put("driver", sql.driver());
        jdbcProps.put("user", sql.user());
        jdbcProps.put("password", sql.password());
        jdbcProps.put("sql", sql.insert());
        jdbcProps.put("sqlParams", sql.sqlParams());
        jdbcProps.put("sqlParamsSeparator", sql.sqlParamsSeparator());
        jdbcProps.put("reconnectTimeMillis", String.valueOf(reconnectTimeMillis));
    }

    @Before
    public void setup() throws Exception {
        LogManager.resetConfiguration();
        db = new JdbcHelper(jdbcProps);
        db.executeDdl(sql.dropTable());
        db.executeDdl(sql.createTable());
    }

    @After
    public void tearDown() throws Exception {
        db.close();
    }

    @Test
    public void shouldLogAllMessageFieldsCorrectlyToTheDatabase() throws Exception {
        try (JdbcHelper db = new JdbcHelper(jdbcProps)) {

            // Given
            Log4jSupport.setupAppender(DiscardingJdbcAppender.class, jdbcProps);

            // When
            NDC.push("some NDC data");
            Logger.getRootLogger().error("some log message", new Exception("some exception"));
            NDC.pop();

            // Then
            List<LogMessage> msgs = db.selectAllLogMessages(sql.selectAll());
            assertEquals(1, msgs.size());
            LogMessage msg = msgs.get(0);
            assertEquals("root", msg.logger);
            assertEquals("ERROR", msg.priority);
            assertEquals(Thread.currentThread().getName(), msg.threadId);
            assertEquals("some NDC data", msg.context);
            assertEquals("some log message", msg.message);
            String expectedTrace = "java.lang.Exception";
            assertEquals(expectedTrace, msg.trace.substring(0, expectedTrace.length()));
        }
    }

    @Test
    public void shouldRecoverAfterDatabaseFailuresDiscardingMessagesDuringOutage() throws Exception {
        // Given
        Log4jSupport.setupAppender(DiscardingJdbcAppender.class, jdbcProps);
        Logger logger = Logger.getRootLogger();
        logger.debug("first message");

        // When
        db.executeDdl(sql.renameTable(sql.tableName(), "templogtable")); // induce a failure
        logger.info("this should fail"); // fail with a database exception
        db.executeDdl(sql.renameTable("templogtable", sql.tableName())); // restore
        logger.info("message after outage");

        // Then
        List<LogMessage> msgs = db.selectAllLogMessages(sql.selectAll());
        assertEquals("first message", msgs.get(0).message);
        assertEquals("message after outage", msgs.get(1).message);
        assertEquals(2, msgs.size());
    }

}
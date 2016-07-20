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

public class AsyncJdbcAppenderIntegrationTest {

    private final Dialects.Dialect sql = Dialects.forDefaultDatabase();
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

    private final Properties asyncProps = new Properties();
    {
        asyncProps.put("locationInfo", true);
        asyncProps.put("maxElements", "500000");
        asyncProps.put("errorReportIntervalMillis", "30000");
        asyncProps.put("warningThreshold", "150000");
        asyncProps.put("gracefulShutdownTimeMillis", "600000");
    }

    private JdbcHelper db;

    @Before
    public void setup() throws Exception {
        LogManager.resetConfiguration();

        db = new JdbcHelper(jdbcProps);
        db.executeDdl(sql.dropTable());
        db.executeDdl(sql.createTable());

        Properties appenderProperties = mergeProperties(jdbcProps, asyncProps);
        Log4jSupport.setupAppender(AsyncJdbcAppender.class, appenderProperties);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
    }

    @Test
    public void shouldLogAllMessageFields() throws Exception {

        // When
        NDC.push("some NDC message");
        Logger.getRootLogger().info("some message");
        NDC.pop();

        Thread.sleep(200); // allow plenty of time for the async db write to complete

        // Then
        LogMessage msg = db.selectAllLogMessages(sql.selectAll()).get(0);
        assertEquals("root", msg.logger);
        assertEquals("INFO", msg.priority);
        assertEquals(Thread.currentThread().getName(), msg.threadId);
        assertEquals("some NDC message", msg.context);
        assertEquals("some message", msg.message);
        assertEquals("", msg.trace);
    }

    @Test
    public void shouldRecoverAfterSqlFailuresAndNotDiscardAnyMessages() throws Exception {
            // Given
            Logger logger = Logger.getRootLogger();
            logger.debug("first message");

            // When
            db.executeDdl(sql.renameTable(sql.tableName(), "templogtable")); // induce a failure
            Thread.sleep(100);
            logger.info("message during outage"); // should fail but be queued
            Thread.sleep(100); // wait long enough for appender to have tried to write the message
            db.executeDdl(sql.renameTable("templogtable", sql.tableName())); // restore

            // wait long enough for appender to have tried to re-write the message we logged during outage
            Thread.sleep(reconnectTimeMillis * 2);

            logger.warn("message after outage");
            Thread.sleep(100); // wait long enough for appender to have tried to write the message

            // Then
            List<LogMessage> msgs = db.selectAllLogMessages(sql.selectAll());
            assertEquals("first message", msgs.get(0).message);
            assertEquals("message during outage", msgs.get(1).message);
            assertEquals("message after outage", msgs.get(2).message);
            assertEquals(3, msgs.size());
    }

    private Properties mergeProperties(Properties... props) {
        Properties all = new Properties();
        for (Properties p : props)
            all.putAll(p);
        return all;
    }

}
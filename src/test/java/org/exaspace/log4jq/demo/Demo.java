package org.exaspace.log4jq.demo;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.exaspace.log4jq.support.Dialects;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;

/**
 * This app can be used to show the memory queue in action, and database logging against different databases.
 *
 * - log4j is first configured to use the async jdbc appender
 * - the app then repeatedly emits log messages (default 10 per second)
 *
 * At the start of the test, the app requires the desired database to actually be running (so it can bootstrap
 * the creation of its demo log table for convenience).
 *
 * If not set, the "log4jq.debug" system property is set to "true" for you (so you can see more of what's going on).
 *
 * You can play with the settings which are defined in properties files under src/test/demo/ for the desired database type.
 * Try stopping the database during the test to simulate a database outage.
 * You will see that log4jq reports warning messages like "Append failed! Will retry after ..."
 * as it can't write the messages to the database.
 *
 * If you have Docker installed, you can easily simulate database failures - see README.md
 *
 * The demo properties file sets the max queue size very low so you can observe what happens if you
 * leave the database unavailable long enough for the queue to fill up. In practice in production of course
 * you would set a far higher queue size so you could survive much longer outages without discarding any messages.
 *
 */
public class Demo {

    /**
     *  Appender name as defined in demo log4j properties file
     */
    public final static String APPENDER_NAME = "JDBC_ASYNC";

    /**
     *  How many log4j calls to make per second
     */
    public final static int DEFAULT_MESSAGES_PER_SECOND = 5;

    static {
        if (null == System.getProperty("log4jq.debug")) {
            System.setProperty("log4jq.debug", "true");
        }
    }

    public static void main(String[] args) throws Exception {

        int msgsPerSec = args.length > 0 ? Integer.parseInt(args[1]) : DEFAULT_MESSAGES_PER_SECOND;

        String database = Dialects.defaultDatabase();
        String propsPath = "/demo/" + database + "/log4j.properties";
        Properties log4jProperties = readPropertiesFromClasspath(propsPath);
        createLogTable(database, log4jProperties);

        try {
            PropertyConfigurator.configure(log4jProperties);
            loop(msgsPerSec);
        } finally {
            LogManager.shutdown();
        }
    }

    private static void loop(int msgsPerSec) throws InterruptedException {
        long intervalMs = 1000 / msgsPerSec;
        long max = 10000000;

        System.out.println("Will now write one message every " + intervalMs + "ms");

        Logger log = Logger.getLogger("JDBC_ASYNC");
        for (int i = 0; i < max; i++) {
            log.info("message " + i);
            Thread.sleep(intervalMs);
        }
    }

    private static void createLogTable(String database, Properties log4jProperties) throws Exception {
        String ddl = Dialects.forDatabase(database).createTable();
        try (Connection conn = newConnection(log4jProperties);
             PreparedStatement stmt = conn.prepareStatement(ddl)) {
            stmt.executeUpdate();
        }
    }

    private static Connection newConnection(Properties properties) throws Exception {
        String prefix = "log4j.appender." + APPENDER_NAME + ".";

        String driver = (String) properties.get(prefix + "driver");
        String url = (String) properties.get(prefix + "url");
        String user = (String) properties.get(prefix + "user");
        String password = (String) properties.get(prefix + "password");

        System.out.println("Using driver: " + driver);
        Class.forName(driver);
        return DriverManager.getConnection(url, user, password);
    }

    private static Properties readPropertiesFromClasspath(String propsPath) throws IOException {
        System.out.println("Attempting to read properties file: " + propsPath);
        Properties p = new Properties();
        try (final InputStream is = Demo.class.getResourceAsStream(propsPath)) {
            p.load(is);
        }
        return p;
    }

}

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
 * - log4j is first configured to use log4jq
 * - the app then repeatedly emits log messages (default 10 per second)
 *
 * At the start of the test, the app requires the desired database to actually be running (so it can bootstrap
 * the creation of its demo log table for convenience).
 *
 * The "log4jq.debug" system property is set to "true" for you (so you can see more of what's going on).
 *
 * You can play with the settings which are defined in properties files under src/test/demo/ for the desired database type.
 * Try stopping the database during the test to simulate a database outage.
 * You will see that log4jq reports warning messages like "Append failed! Will retry after ..."
 * as it can't write the messages to the database.
 *
 * Using Docker is ideal for this as you can easily simulate database failures.
 *
 *    docker run --name postgres -p 5432:5432 postgres
 *
 *    run the app with the argument "postgres"
 *
 *    observe messages being inserted in the database
 *
 *    docker stop postgres
 *
 *    observe messages being buffered to memory
 *
 *    docker start postgres
 *
 *    observe the buffered messages being rapidly inserted to the database
 *
 *  The default demo settings are set artificially low so you can observe quickly what happens if you
 *  leave the database unavailable long enough for the queue to fill up. In practice in production you
 *  would set a much higher queue size so you could survive much longer outages without discarding messages.
 */
public class Demo {

    // This must match the appender name as defined in the demo log4j properties file
    public final static String APPENDER_NAME = "JDBC_ASYNC";

    public static void main(String[] args) throws Exception {

        System.setProperty("log4jq.debug", "true");

        if (args.length < 1) {
            System.err.println("Supply database type (e.g. 'postgres')");
            return;
        }

        String database = args[0];
        System.out.println("Using database=" + database);
        int msgsPerSec = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        Dialects.Dialect dialect = Dialects.forDatabase(database);
        Properties log4jProperties = readProperties("/demo/" + database + "/log4j.properties");
        try (final Connection conn = newConnection(log4jProperties)) {
            createTable(conn, dialect.createTable());
        }

        PropertyConfigurator.configure(log4jProperties);

        long intervalMs = 1000 / msgsPerSec;
        long MAX = 10000000;
        System.out.println(
                "Will now write one message every " + intervalMs + "ms" +
                " (up to a maximum of " + MAX + " just for sanity)");

        Logger log = Logger.getLogger("JDBC_ASYNC");
        for (int i = 0; i < MAX; i++) {
            log.info("message " + i);
            Thread.sleep(intervalMs);
        }

        LogManager.shutdown();
    }

    private static Connection newConnection(Properties properties) throws Exception {
        String prefix = "log4j.appender." + APPENDER_NAME + ".";

        String driver = (String) properties.get(prefix + "driver");
        String url = (String) properties.get(prefix + "url");
        String user = (String) properties.get(prefix + "user");
        String password = (String) properties.get(prefix + "password");

        System.out.println("Using " + driver);
        Class.forName(driver);
        return DriverManager.getConnection(url, user, password);
    }

    private static void createTable(Connection conn, String sql) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    private static Properties readProperties(String propsPath) throws IOException {
        Properties p = new Properties();
        try (final InputStream is = Demo.class.getResourceAsStream(propsPath)) {
            p.load(is);
        }
        return p;
    }

}

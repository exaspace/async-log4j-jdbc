package org.exaspace.log4jq.support;

public class Dialects {

    public static String defaultDatabase() {
        String ret = System.getProperty("database", System.getenv("DATABASE"));
        return ret != null ? ret : "hsql";
    }

    public static Dialect forDefaultDatabase() {
        return forDatabase(defaultDatabase());
    }

    public static Dialect forDatabase(final String database) {
        Dialect ret;
        switch (database) {
            case "hsql":
                ret = new HsqlDialect();
                break;
            case "postgres":
                ret = new PostgresDialect();
                break;
            default:
                throw new RuntimeException("Unknown test database type specified: " + database);
        }
        return ret;
    }

    public interface Dialect {

        String tableName();

        String createTable();

        String dropTable();

        String renameTable(String from, String to);

        String selectAll();

        String insert();

        String sqlParams();

        String sqlParamsSeparator();

        String url();

        String driver();

        String user();

        String password();
    }

    private static abstract class StandardDialect implements Dialect {

        @Override
        public String tableName() {
            return "applog";
        }

        @Override
        public String dropTable() {
            return "DROP TABLE IF EXISTS applog";
        }

        @Override
        public String renameTable(String from, String to) {
            return String.format("ALTER TABLE %s RENAME TO %s", from, to);
        }

        @Override
        public String selectAll() {
            return "  SELECT id, logdate, logger, priority, threadid, context, message, trace " +
                    " FROM applog" +
                    " ORDER BY logdate ASC";
        }

        @Override
        public String insert() {
            return "INSERT INTO applog (LogDate, Logger, Priority, ThreadID, Context, Message, Trace) VALUES (CURRENT_DATE, ?, ?, ?, ?, ?, ?)";
        }

        @Override
        public String sqlParams() {
            return "%c, %p, %t, %x, %m, %throwable"; // cat, prio, thread, ndc, message, exception
        }

        @Override
        public String sqlParamsSeparator() {
            return ",";
        }

    }

    private static class HsqlDialect extends StandardDialect {

        @Override
        public String createTable() {
            return "CREATE TABLE applog (\n" +
                    "      ID int identity NOT NULL,\n" +
                    "      LogDate datetime NOT NULL,\n" +
                    "      Logger varchar(100) NOT NULL,\n" +
                    "      Priority varchar(20) NOT NULL,\n" +
                    "      ThreadID varchar(50) NULL,\n" +
                    "      Context varchar(100) NOT NULL,\n" +
                    "      Message varchar(255) NULL,\n" +
                    "      Trace clob NULL)";
        }

        @Override
        public String url() {
            return "jdbc:hsqldb:mem:test";
        }

        @Override
        public String driver() {
            return "org.hsqldb.jdbc.JDBCDriver";
        }

        @Override
        public String user() {
            return "";
        }

        @Override
        public String password() {
            return "";
        }

    }

    private static class PostgresDialect extends StandardDialect {

        @Override
        public String createTable() {
            return "CREATE TABLE IF NOT EXISTS applog (\n" +
                    "      ID serial NOT NULL,\n" +
                    "      LogDate timestamp NOT NULL,\n" +
                    "      Logger varchar(100) NOT NULL,\n" +
                    "      Priority varchar(20) NOT NULL,\n" +
                    "      ThreadID varchar(50) NULL,\n" +
                    "      Context varchar(100) NOT NULL,\n" +
                    "      Message varchar(255) NULL,\n" +
                    "      Trace text NULL)";
        }

        @Override
        public String url() {
            return "jdbc:postgresql://localhost/postgres";
        }

        @Override
        public String driver() {
            return "org.postgresql.Driver";
        }

        @Override
        public String user() {
            return "demo/postgres";
        }

        @Override
        public String password() {
            return "";
        }
    }

}
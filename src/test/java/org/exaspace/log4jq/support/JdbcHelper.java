package org.exaspace.log4jq.support;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class JdbcHelper implements AutoCloseable {

    private Connection conn;

    public JdbcHelper(Properties props) throws IOException, SQLException {
        Objects.requireNonNull(props);
        conn = DriverManager.getConnection(props.getProperty("url"), props);
    }

    @Override
    public void close() {
        try {
            if (null != conn)
                conn.close();
        } catch (SQLException ignored) {}
        conn = null;
    }

    public ResultSet executeSql(String sql) throws SQLException {
        return conn.createStatement().executeQuery(sql);
    }

    public void executeDdl(String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int rc = stmt.executeUpdate();
            if (rc != 0)
                throw new IllegalStateException(String.format("DDL statement failed: %s", sql));
        }
    }

    public List<LogMessage> selectAllLogMessages(String selectAllSql) throws Exception {
        try (ResultSet rs = executeSql(selectAllSql)) {
            List<LogMessage> ret = new ArrayList();
            while(rs.next()) {
                ret.add(logMessageFromResultSet(rs));
            }
            return ret;
        }
    }

    private LogMessage logMessageFromResultSet(ResultSet rs) throws SQLException {
        LogMessage m = new LogMessage();
        m.id = rs.getLong(1);
        m.logdate = rs.getDate(2);
        m.logger = rs.getString("logger");
        m.priority = rs.getString("priority");
        m.threadId = rs.getString("threadid");
        m.context = rs.getString("context");
        m.message = rs.getString("message");
        m.trace = rs.getString("trace");

        return m;
    }

}
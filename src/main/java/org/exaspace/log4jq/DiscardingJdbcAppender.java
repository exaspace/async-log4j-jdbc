package org.exaspace.log4jq;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 *
 * Blocking (synchronous) database log4j appender which simply discards messages during a database outage
 * (defined as any time the JDBC insert throws an exception) and then attempts to reconnect periodically, 
 * at which point logging will continue.
 *
 * The appender connects using DriverManager.getConnection().
 *
 * The appender uses a prepared statement.
 *
 * Example configuration:
 *
 <pre>
 log4j.appender.JDBC_DISC = org.exaspace.log4jq.DiscardingJdbcAppender
 log4j.appender.JDBC_DISC.url = jdbc:sqlserver://localhost:1433;loginTimeout=10;database=mylogdb;applicationName=testapp;workstationID=mypc;
 log4j.appender.JDBC_DISC.driver = com.microsoft.sqlserver.jdbc.SQLServerXADataSource
 log4j.appender.JDBC_DISC.user = user
 log4j.appender.JDBC_DISC.password = secret
 log4j.appender.JDBC_DISC.sql = exec sp_mylogproc ?, ?, ?, ?, ?, ?
 log4j.appender.JDBC_DISC.sqlParams = %F:%L, %p, %t, %x, %m, %throwable
 log4j.appender.JDBC_DISC.sqlParamsSeparator = ,
 log4j.appender.JDBC_DISC.reconnectTimeMillis = 5000
 </pre>
 *
 */
public class DiscardingJdbcAppender extends AppenderSkeleton implements Appender {

	protected static class JdbcConfig {
		public String url;
		public String user;
		public String password;
		public String driver;
		
		/**
		 * A string of SQL containing wildcard '?' characters for each column
		 * or stored procedure parameter. 
		 */
		public String sql;

		/** 
		 * A list of arbitrary log4j format strings which are valid for EnhancedPatternLayout
		 * separated by sqlParamsSeparator, one for each wildcard '?' character present in 
		 * the configured sql string. 
		 */ 
		public String sqlParams;
		
		/** 
		 * The string that separates the parameters within the sqlParams string
		 * (default is a comma ","). 
		 */
		public String sqlParamsSeparator = ",";
		
		/**
		 * Time (ms) within which failed db connection will not be re-attempted.
		 * This should be higher than the natural connection timeout of the 
		 * JDBC driver under normal conditions.  
		 */
		public int reconnectTimeMillis = 10000;
		
	}

    /*
     * Send debugging information to the console if system property "log4jq.debug" is set.
     */
	private static boolean DEBUG = Boolean.getBoolean("log4jq.debug");

	private JdbcConfig config;
	private JdbcConfig pendingConfig;
	
	private Connection connection;
	private PreparedStatement statement;
	private EnhancedPatternLayout[] layouts;
	private long lastFailedConnectTimeMillis;

	public DiscardingJdbcAppender() {
		resetState();
	}

	private final void resetState() {
		closePatterns(); 
		closeConnection();
		config = null;
		pendingConfig = new JdbcConfig();
		lastFailedConnectTimeMillis = 0;		
	}
	
	@Override
	public boolean requiresLayout() {
		return false;
	}

	/*
	 * Although Log4J never re-uses appenders, an instance of this class   
	 * could actually be safely re-used. 
	 * The close() method closes resources and resets state to the default.
	 * This method can be safely called multiple times in sequence.
	 */
	@Override
	public synchronized void close() {
		this.closed = true; // set Log4J framework superclass flag
		debug("appender close() (e.g. shutdown or reconfigure)");
		resetState();
	}	

	@Override
	public void activateOptions() {
		debug("activateOptions() entry");
		this.config = this.pendingConfig;
		try {
			loadPatterns();
			loadDriver();
		} 
		catch (ClassNotFoundException e) {
			this.config = null;
			error("FATAL - LOGGING DISABLED - could not create Appender", e);
		}
	}

	/**
	 * Check if the configuration has been loaded (enables clients to check if appends will be discarded).
	 */
	public final boolean isConfiguredSuccessfully() {
		return this.config != null;
	}

	@Override
	public void append(LoggingEvent event) {
		appendEvent(event);
	}
	
	public boolean appendEvent(LoggingEvent event) {
		if (this.config == null) 
			return false;
		if (this.connection == null) {
			throttledConnect();
			if (this.connection == null) {
				return false;
			}
		}
		return insert(event);
	}
	
	protected void throttledConnect() {
		assert(this.connection == null);
		long now = System.currentTimeMillis();
		if(lastFailedConnectTimeMillis < now - config.reconnectTimeMillis) {
			try {
				newConnection();
			} 
			catch(SQLException e) {
				lastFailedConnectTimeMillis = now;
				error("Re-connect attempt failed " + e.getMessage(), null);
			}
		}
	}
	
	protected boolean insert(LoggingEvent event) {
		int rc = 0;
		try {
			if (statement == null) 
				statement = this.connection.prepareStatement(config.sql);
			for (int i = 0; i < layouts.length; i++)
				statement.setString(i + 1, layouts[i].format(event));
			rc = statement.executeUpdate();
			if (rc != 1) 
				errorWrite("executeUpdate() returned " + rc + " (1 expected)", null);
            else
                if (DEBUG) debug("Inserted message: " + event.getMessage());
		} catch (SQLException e) {
			errorWrite("Exception during insert so closing connection", e);
			closeConnection();
		}
		return rc == 1;
	}

	protected void loadDriver() throws ClassNotFoundException {
		Class.forName(config.driver);
	}
	
	protected void newConnection() throws SQLException {
		debug("Attempting to connect to jdbc url " + config.url);
		Connection c = obtainConnection();
		if (c.isClosed()) {
			error("Driver returned a closed connection!", null);
		}
		else if(!c.isValid(10)) { // only wait max of 10 secs for validity check
			error("Driver returned a invalid connection!", null);
		}
		else {
			debug("Connection SUCCESS " + c);
			c.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
			c.setAutoCommit(true);
			this.connection = c;
		}
	}
	
	protected Connection obtainConnection() throws SQLException {
		return DriverManager.getConnection(config.url, config.user, config.password);
	}

	/*
	 * Close database resources and connection.
	 */
	protected void closeConnection() {
		try {
			if (this.statement != null) 
				this.statement.close();
		} 
		catch (SQLException ignored) {}
		this.statement = null;
		try {
			if (this.connection != null) {
				this.connection.close();
				debug("closed jdbc connection " + this.connection);
			}
		}
		catch(SQLException ignored) {}
		this.connection = null;
	}
	
	/*
	 * Build and store an EnhancedPatternLayout for each SQL parameter string.
	 */
	protected void loadPatterns() {
		int numParams = countMatches(config.sql, "?");
		String[] frags = config.sqlParams.split("\\s*" + config.sqlParamsSeparator + "\\s*");
		layouts = new EnhancedPatternLayout[frags.length];
		if (frags.length != numParams) {
			throw new IllegalArgumentException("SQL has " + numParams + 
					" wildcards but sqlParams defines only " + frags.length);
		}
		else {
			for (int i=0; i<frags.length; i++) 
				layouts[i] = new EnhancedPatternLayout(frags[i]);
		}
	}

    private static int countMatches(final String str, final String sub) {
        if (isEmpty(str) || isEmpty(sub)) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    /*
	 * Null out patterns to avoid potential memory leaks.
	 */
	protected void closePatterns() {
		if (layouts != null) {
			for (int i=0; i<layouts.length; i++) {
				layouts[i] = null;
			}
			layouts = null;
		}
	}

	protected void debug(String msg) {
		if (DEBUG)
            System.out.println(getClass().getName() + " " + hashCode() + " " + msg);
	}
	
	protected void error(String msg, Exception e) {
		error(msg, e, ErrorCode.GENERIC_FAILURE);		
	}

	protected void errorWrite(String msg, Exception e) {
		error(msg, e, ErrorCode.WRITE_FAILURE);		
	}

	protected void error(String msg, Exception e, int code) {
		if (msg == null && e != null) msg = e.getMessage();
		errorHandler.error(getClass().getName() +  " " + hashCode() + " " + msg, e, code);
	}
	
	protected JdbcConfig getConfig() {
		return config;
	}

	protected JdbcConfig getPendingConfig() {
		return pendingConfig;
	}

	// config bean method
	public void setUrl(String url) {
		pendingConfig.url = url;
	}

	// config bean method
	public void setUser(String user) {
		pendingConfig.user = user;
	}

	// config bean method
	public void setPassword(String password) {
		pendingConfig.password = password;
	}

	// config bean method
	public void setSql(String sql) {
		pendingConfig.sql = sql;
	}

	// config bean method
	public void setSqlParams(String sqlParams) {
		pendingConfig.sqlParams = sqlParams;
	}
	
	// config bean method
	public void setSqlParamsSeparator(String sqlParamsSeparator) {
		pendingConfig.sqlParamsSeparator = sqlParamsSeparator;
	}
	
	// config bean method
	public void setDriver(String driver) {
		pendingConfig.driver = driver;
	}

	// config bean method
	public void setReconnectTimeMillis(int reconnectTimeMillis) {
		pendingConfig.reconnectTimeMillis = reconnectTimeMillis;
	}
	
}

    ____  ____ ___  _ _      ____    _     ____  _____    _     _
    /  _ \/ ___\\  \/// \  /|/   _\  / \   /  _ \/  __//\ / |   / |
    | / \||    \ \  / | |\ |||  /    | |   | / \|| |  _\_\| |   | |
    | |-||\___ | / /  | | \|||  \_   | |_/\| \_/|| |_//   | |/\_| |
    \_/ \|\____//_/   \_/  \|\____/  \____/\____/\____\   \_|\____/


# Asynchronous database logging (Log4J version).

Log resiliently and robustly to a central database via JDBC.

* minimal runtime logging overhead (async hand off)
* use industry standard logging framework - log4j - with no custom code needed
* a tiny 12KB jar with no dependencies (apart from the log4j framework itself)
* used in production for years in a large Fintech company
* simple configuration via normal log4j mechanisms

Benefits:

* collect log messages from multiple JVMs in realtime in a central location
* isolate calling threads from I/O bottle necks
* store log messages with standard log4j meta data (e.g. timestamp, priority, source file line number, etc.)
* search your app logs in realtime with a proper query language - SQL
* no new systems to support - backup and support of your RDBMS is already well understood


## What is this library?

Simply a log4j appender `org.exaspace.log4jq.AsyncJdbcAppender` which you can drop straight into your application to send your application logs asynchronously and robustly to Postgres, MySQL or any other database server which has a JDBC driver.

The appender isolates your application threads from database slowdowns or outages. Log events are queued in memory if the database goes offline or cannot keep up with intense bursts of logging activity. The appender re-connects automatically to write any queued log events when the database comes back online. Logging is done asynchronously so your application threads don't block trying to do database I/O.

Most importantly this library has been extensively battle-tested in production.

### Performance comparison

| Appender                                   | Msgs Per Second    |
| ---------------------                      | -----------------: |
| org.exaspace.log4jq.AsyncJdbcAppender      | 2908               |
| org.exaspace.log4jq.DiscardingJdbcAppender | 865                |
| org.apache.log4j.jdbc.JDBCAppender         | 437                |
| org.apache.log4j.jdbcplus.JDBCAppender     | 364                |

This example: Quad Core i7, 16GB RAM, MS Sql Server, XA driver, App & DB on same machine.


### Appenders Provided

`org.exaspace.log4jq.AsyncJdbcAppender`

This is the main one you will want to use: every logging call is offloaded immediately to a memory queue. From there, a background writer thread picks off the log events and writes them as quickly as possible to the database. Uses the `DiscardingJdbcAppender`.

`org.exaspace.log4jq.DiscardingJdbcAppender`

This appender is used by the async appender but can also be used directly. It is a blocking (synchronous) database appender which simply discards messages during a database outage
(an outage is defined as any time that the JDBC insert throws an exception) and then attempts to reconnect periodically,
at which point logging will continue. A connection is opened and held open while the appender is managed by the log4j system.
The log event is inserted to the database via a prepared statement.


### See a demo!

Clone this repo then:

    # Start a database server. The demo app will create a table "applog".

    $ docker run --name postgres -p 5432:5432 postgres

    # By default the log4j config connects to a host called 'postgres' so
    # either add that entry to your /etc/hosts (i.e. probably localhost or the
    # address of your docker machine). Alternatively, modify the hostname directly in the log4j props file.

    # Run the demo app which configures log4j via "src/test/resources/demo/postgres/log4j.properties"
    # and then goes into a loop calling logger.info() 10 times per second

    $ ./gradlew -Pdatabase=postgres demo


This runs the `org.exaspace.log4jq.demo.Demo` application which just repeatedly calls logger.info(). You should see that application log messages are being inserted into the database (in this case, a table called "applog"). To see the count of messages:

    $ psql -h postgres -U postgres -d postgres -c 'select count(*) from applog'

Or if you don't have `psql` installed on your machine, you can run psql from a docker container:

    $ docker run --rm --link postgres -it postgres psql -h postgres -U postgres \
                  -d postgres -c 'select count(*) from applog'

Then try

    $ docker stop postgres
    # ... observe messages being buffered to memory while the database is down

    $ docker start postgres
    # ...observe the memory buffered messages being rapidly inserted to the database when it comes back up


### Design

* application thread calling any logging method will hand off the log event immediately
* a memory queue is used to buffer log events (`java.util.concurrent.BlockingDeque`)
* log events are written to the database in the background
* a single writer background thread is used (avoids further locking and gives highest write throughput)
* automatically re-connects at a throttled rate if the database is disconnected (or on any form of SQL exception)
* outputs warning messages if number of messages in the queue exceeds your configured warning threshold
* outputs warning messages if the queue is full
* configurable drain time once the application shuts down

### Usage

Just add the jar to your project/classpath and alter your existing log4j configuration.

    log4j.appender.JDBC_ASYNC = org.exaspace.log4jq.AsyncJdbcAppender
    log4j.appender.JDBC_ASYNC.sqlParamsSeparator = ,
    log4j.appender.JDBC_ASYNC.sqlParams = %F:%L, %p, %t, %x, %m, %throwable
    log4j.appender.JDBC_ASYNC.sql = INSERT INTO applog (LogDate, Logger , Priority ,ThreadID , Context, Message , Trace) VALUES (getDate(), ?, ?, ?, ?, LEFT(ISNULL(?,''),6000), ?)
    log4j.appender.JDBC_ASYNC.locationInfo = true
    log4j.appender.JDBC_ASYNC.reconnectTimeMillis = 5000
    log4j.appender.JDBC_ASYNC.maxElements = 500000
    log4j.appender.JDBC_ASYNC.errorReportIntervalMillis = 30000
    log4j.appender.JDBC_ASYNC.warningThreshold = 150000
    log4j.appender.JDBC_ASYNC.gracefulShutdownTimeMillis = 600000
    log4j.appender.JDBC_ASYNC.user = testing
    log4j.appender.JDBC_ASYNC.password = unitTESTING
    log4j.appender.JDBC_ASYNC.driver = com.microsoft.sqlserver.jdbc.SQLServerXADataSource
    log4j.appender.JDBC_ASYNC.url = jdbc:sqlserver://localhost:1433;loginTimeout=10;database=logdb;applicationName=logtest;workstationID=mypc;

See the `log4j.example.properties` file for a full description.

### Verbose mode

You can set system property "log4jq.debug" to "true" to output more internal information to the stdout.

    -Dlog4jq.debug=true

The asynchronous logger outputs warnings via log4j's LogLog.warn() method.

### References and Notes

The other JDBC appender implementations referred to are:

* [Standard JDBC appender](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/jdbc/JDBCAppender.html)
* [JDBC Plus](http://www.mannhaupt.com/danko/projects/jdbcappender/doc/index.html)

The performance test figures with the different appenders were obtained by using the test code in this project using the following configuration:

	LOOPS_PER_THREAD = 1000;
	NUM_THREADS = 15;
	MESSAGE_INTERVAL_MILLIS = 10;
	SYSOUT_MODULUS = 1000;

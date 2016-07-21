    ____  ____ ___  _ _      ____    _     ____  _____    _     _
    /  _ \/ ___\\  \/// \  /|/   _\  / \   /  _ \/  __//\ / |   / |
    | / \||    \ \  / | |\ |||  /    | |   | / \|| |  _\_\| |   | |
    | |-||\___ | / /  | | \|||  \_   | |_/\| \_/|| |_//   | |/\_| |
    \_/ \|\____//_/   \_/  \|\____/  \____/\____/\____\   \_|\____/

[![Build Status](https://travis-ci.org/exaspace/async-log4j-jdbc.svg?branch=master)](https://travis-ci.org/exaspace/async-log4j-jdbc)

# Asynchronous database logging (Log4J version).

Log easily, resiliently and robustly to a central database via JDBC.

No coding needed, just update your Log4J configuration file.

Why log to an RDBMS?

* collect log messages easily from multiple JVMs in realtime in a central location
* use any database that provides a JDBC driver
* store log messages with standard Log4J pattern meta data (e.g. timestamp, priority, source file line number, etc.)
* search your app logs in realtime with a proper query language - SQL
* no new systems to support - backup and support of your RDBMS is already well understood


## What is this library?

A Log4J appender `org.exaspace.log4jq.AsyncJdbcAppender` which you drop straight into your application to send
your log messages asynchronously and robustly to any database that has a JDBC driver.

Logging is done asynchronously so that your application threads don't block trying to do the actual database I/O.

If your log database becomes slow or even totally unavailable, your application is not impacted and continues to log as normal.

The appender re-connects automatically to write any queued log events when the database comes back online.

This appender has been battle tested in high volume production for years in a large financial services company.


### Performance

| Appender                                   | Msgs Per Second    |
| ---------------------                      | -----------------: |
| org.exaspace.log4jq.AsyncJdbcAppender      | 2908               |
| org.exaspace.log4jq.DiscardingJdbcAppender | 865                |
| org.apache.log4j.jdbc.JDBCAppender         | 437                |
| org.apache.log4j.jdbcplus.JDBCAppender     | 364                |

This example: Quad Core i7, 16GB RAM, MS Sql Server, XA driver, App & DB on same machine.


### Design

* application thread calling any logging method will hand off the log event immediately
* a memory queue is used to buffer log events (`java.util.concurrent.BlockingDeque`)
* log events are written to the database in the background
* a single writer background thread is used (avoids further locking and gives highest write throughput)
* automatically re-connects at a throttled rate if the database is disconnected (or on any form of SQL exception)
* outputs warning messages if number of messages in the queue exceeds your configured warning threshold
* outputs warning messages if the queue is full
* configurable drain time once the application shuts down
* a tiny 12KB jar with no dependencies (apart from Log4J of course)
* minimal runtime logging overhead


### Appenders Provided

`org.exaspace.log4jq.AsyncJdbcAppender`

This is the main one you will want to use: every logging call is offloaded immediately to a memory queue. From there, a background writer thread picks off the log events and writes them as quickly as possible to the database. Uses the `DiscardingJdbcAppender`.

`org.exaspace.log4jq.DiscardingJdbcAppender`

This appender is used by the async appender but can also be used directly. It is a blocking (synchronous) database appender which simply discards messages during a database outage
(an outage is defined as any time that the JDBC insert throws an exception) and then attempts to reconnect periodically,
at which point logging will continue. A connection is opened and held open while the appender is managed by the log4j system.
The log event is inserted to the database via a prepared statement.

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

You can set system property "log4jq.debug" to "true" to output more internal information to stdout.

    -Dlog4jq.debug=true

Warning use Log4J's internal standard LogLog.warn() method.


### See a demo!

First clone this repo and cd into it, then:

    docker run -d --name postgres -p 5432:5432 postgres
    docker build -t demo .
    docker run --rm --name demo --link postgres demo

The demo app configures Log4J (see "src/test/resources/demo/postgres/log4j.properties"), creating a table called `applog` to hold the messages
and then goes into a loop calling logger.info() (5 times per second by default).
You will see the database write logged for each message (as the demo is running in a chatty debug mode):

    org.exaspace.log4jq.DiscardingJdbcAppender 1174290147 Inserted message: message 0
    org.exaspace.log4jq.DiscardingJdbcAppender 1174290147 Inserted message: message 1
    org.exaspace.log4jq.DiscardingJdbcAppender 1174290147 Inserted message: message 2
    ...

You can keep an eye on how many messages have been inserted into the database:

    docker exec postgres psql -U postgres -d postgres -c 'select count(*) from applog'

Ok, now let's stop the log database!

    docker stop postgres  # we will soon observe messages being buffered to memory while the database is down...

As soon as docker actually stops the database, you will see an exception logged by the appender, and it will tell you that log messages are being queued in RAM
(and will report periodic information about the queue size).

    ...
    org.exaspace.log4jq.DiscardingJdbcAppender 1607460018 Inserted message: message 110
    org.exaspace.log4jq.DiscardingJdbcAppender 1607460018 Inserted message: message 111
    log4j:ERROR org.exaspace.log4jq.DiscardingJdbcAppender 1607460018 Exception during insert so closing connection
    Append failed! Will retry after 4100ms
    org.exaspace.log4jq.DiscardingJdbcAppender 1607460018 Attempting to connect to jdbc url jdbc:postgresql://postgres/postgres
     size=37 (7.0% full) discards=0 submitted=150 avail=463 capacity=500 freeVmBytes=27802032
     size=87 (17.0% full) discards=0 submitted=200 avail=413 capacity=500 freeVmBytes=27802032
     size=137 (27.0% full) discards=0 submitted=250 avail=363 capacity=500 freeVmBytes=27623776
     ...

As you can see, no inserts are now logged. The queue is filling up quickly! The demo has deliberately configured a tiny capacity memory queue of only 500 messages!

Ok now let's start the database again:

    docker start postgres  # we will soon observe the memory buffered messages being rapidly inserted to the database when it comes back up

There's a bit of a lag due to docker machinery, but once the container is linked again, you'll see the appender re-connect and insert messages where it left off.

    log4j:WARN Queue size exceeds your configured warning threshold  size=326 (65.0% full) discards=0 submitted=439 avail=174 capacity=500 freeVmBytes=27267144
    org.exaspace.log4jq.DiscardingJdbcAppender 1607460018 Connection SUCCESS org.postgresql.jdbc.PgConnection@200ca29f
    org.exaspace.log4jq.DiscardingJdbcAppender 1607460018 Inserted message: message 112
    org.exaspace.log4jq.DiscardingJdbcAppender 1607460018 Inserted message: message 113
    org.exaspace.log4jq.DiscardingJdbcAppender 1607460018 Inserted message: message 114
    ...

If you leave the database offline long enough for the queue to fill up, you'll see error messages being reported that new messages are now being discarded. Again, once
the database is restarted, the appenender will connect again and write its queue to the database as quickly as possible.

#### Running the demo without Docker:

You'll need postgresql installed and running.

1. Make sure postgres is running
2. Configure your postgres database details in "src/test/resources/demo/postgres/log4j.properties".
3. Run `./gradlew -Pdatabase=postgres demo`  (the task `demo` runs the Java class `org.exaspace.log4jq.demo.Demo`)
4. psql into your database and run `select count(*) from applog` at any time to see how many messages have been stored

#### Selecting a database for demo

You have to tell the demo class which type of database dialect to use.
The database to use can be specified either as an environment variable as in `DATABASE=postgres ./gradlew demo` or as a gradle property `./gradlew -Pdatabase=postgres demo`.
If you are running the demo in a docker container then you can use the `-e` flag to pass the environment variable as in `docker run -e DATABASE=postgres ...`

TODO: Currently only the value `postgres` is supported.


### References and Notes

The other JDBC appender implementations referred to are:

* [Standard JDBC appender](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/jdbc/JDBCAppender.html)
* [JDBC Plus](http://www.mannhaupt.com/danko/projects/jdbcappender/doc/index.html)

The performance test figures with the different appenders were obtained by using the test code in this project using the following configuration:

	LOOPS_PER_THREAD = 1000;
	NUM_THREADS = 15;
	MESSAGE_INTERVAL_MILLIS = 10;
	SYSOUT_MODULUS = 1000;

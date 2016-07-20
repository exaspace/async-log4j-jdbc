package org.exaspace.log4jq;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Asynchronous database appender.
 *
 * Supports the same configuration options as DiscardingJdbcAppender.
 *
 * Additional configuration options for this appender:
 *
 * <pre>
 *
 * # you need this (slow!) if using location pattern conversions like %F
 * log4j.appender.JDBC_ASYNC.locationInfo = true
 *
 * # the max size of the memory queue
 * log4j.appender.JDBC_ASYNC.maxElements = 1000000
 *
 * # how often to report message discard warnings (recommended at least 1000)
 * # set to -1 to disable error reporting (will generate a warning)
 * log4j.appender.JDBC_ASYNC.errorReportIntervalMillis = 60000
 *
 * # report a periodic warning if number of messages in queue exceeds given value
 * log4j.appender.JDBC_ASYNC.warningThreshold = 100000
 *
 * # how long to process messages still in queue after shutdown
 * log4j.appender.JDBC_ASYNC.gracefulShutdownTimeMillis = 60000;
 *
 * </pre>
 *
 * @see DiscardingJdbcAppender
 */
public class AsyncJdbcAppender extends AppenderSkeleton implements Appender {

    final class LogWriterThread extends Thread {

        private final DiscardingJdbcAppender appender;
        private int count = 0;

        private LogWriterThread(DiscardingJdbcAppender a) {
            this.appender = a;
        }

        public void run() {
            debug("JDBC LOG WRITER THREAD STARTED");
            long breakTime = -1;
            try {
                while (true) {
                    if (breakTime == -1 && isInterrupted()) {
                        breakTime = System.currentTimeMillis() + gracefulShutdownTimeMillis;
                        debug("Will exit in millis: " + breakTime);
                    }
                    if (breakTime != -1 && System.currentTimeMillis() >= breakTime) {
                        debug("Break time reached");
                        break;
                    }
                    try {
                        synchronized (AsyncJdbcAppender.this) {
                            if (closed && reservations == 0)
                                break;
                        }
                        LoggingEvent event = queue.take();
                        synchronized (AsyncJdbcAppender.this) {
                            --reservations;
                        }
                        write(event);
                    } catch (InterruptedException e) {
                        info("INTERRUPTED");
                    }
                }
            } finally {
                appender.close();
            }
            if (reservations > 0)
                warn(SHUTDOWN_DISCARD, reservations);
            else
                info(SHUTDOWN_OK);
        }

        void write(final LoggingEvent event) throws InterruptedException {
            do {
                count++;
                if (closed && reservations > 0 && count % 1000 == 0) {
                    info("Clearing queue. Remaining=" + reservations);
                }
                if (appender.appendEvent(event)) {
                    break;
                }
                long sleepTime = appender.getConfig().reconnectTimeMillis + 100;
                debug("Append failed! Will retry after " + sleepTime + "ms");
                Thread.sleep(sleepTime);
            }
            while (!closed);
        }
    }

    /*
     * Send debugging information to the console if system property "log4jq.debug" is set.
     */
    private static boolean DEBUG = Boolean.getBoolean("log4jq.debug");

    final static String THRESHOLD_WARNING =
            "Queue size exceeds your configured warning threshold";

    final static String DISCARD_WARNING =
            "Discarding message! Log queue is full (consider increasing maxElements)";

    final static String SHUTDOWN_OK =
            "Thread exiting - no messages were lost";

    final static String SHUTDOWN_DISCARD =
            "Thread exiting, %d messages still in queue will be lost! (consider increasing gracefulShutdownTimeMillis)";

    final static String REPORT_WARNING =
            "Discarded log messages will not be reported (see errorReportIntervalMillis)";

    /**
     * Config Option. Call getLocationInformation() on LoggingEvent or not.
     * Slow - but required if using filename/line conversion patterns.
     */
    private boolean locationInfo = true;

    /**
     * Config Option. Max messages to hold in memory.
     */
    private int maxElements = 1000000;

    /**
     * Config Option. Report at most 1 discarded event within this interval.
     * Set to any negative value to disable error reporting.
     */
    private long errorReportIntervalMillis = -1;

    /**
     * Config Option. Report a periodic warning if number of messages in queue
     * exceeds given value.
     */
    private int warningThreshold = 50000;

    /**
     * Config Option. How long to process messages still in queue after shutdown.
     */
    private long gracefulShutdownTimeMillis = 60000;

    private BlockingDeque<LoggingEvent> queue;
    private int reservations;
    private int discarded;
    private int submitted;
    private LogWriterThread writerThread;
    private long lastReportedTimeMillis;

    public AsyncJdbcAppender() {
        DiscardingJdbcAppender appender = new DiscardingJdbcAppender();
        writerThread = new LogWriterThread(appender);
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    @Override
    public synchronized void activateOptions() {
        if (this.closed) {
            error("Attempt to re-activate closed appender");
            return;
        }
        if (errorReportIntervalMillis < 0)
            warn(REPORT_WARNING);

        queue = new LinkedBlockingDeque<LoggingEvent>(maxElements);

        writerThread.appender.activateOptions();
        if (writerThread.appender.isConfiguredSuccessfully())
            writerThread.start();
        else
            queue = null;
    }

    @Override
    public synchronized void close() {
        this.closed = true; // set Log4J framework superclass flag
        if (writerThread != null) {
            debug("calling interrupt on writer " + writerThread.getName());
            writerThread.interrupt();
        }
        writerThread = null;
    }

    /*
     * AppenderSkeleton has already synchronized on "this" for the append.
     */
    @Override
    public void append(LoggingEvent event) {
        if (this.closed) {
            error("Attempted append to closed appender.");
            return;
        }
        ++submitted;
        // ensure thread specific fields are correctly populated
        event.getNDC();
        event.getThreadName();
        event.getMDCCopy();
        if (locationInfo) {
            event.getLocationInformation();
        }
        boolean added = queue.offer(event);
        if (added) {
            ++reservations;
        } else {
            ++discarded;
        }
        final boolean alert = reservations > warningThreshold;
        if (errorReportIntervalMillis >= 0 &&
                (!added || alert)) {
            long now = System.currentTimeMillis();
            long due = lastReportedTimeMillis + errorReportIntervalMillis;
            if (now > due) {
                lastReportedTimeMillis = now;
                if (!added)
                    warn(DISCARD_WARNING);
                else if (alert)
                    warn(THRESHOLD_WARNING);
            }
        }
        if (DEBUG && submitted % 50 == 0)
            debug(getStateInfo());
    }

    public String getStateInfo() {
        final int avail = maxElements - reservations;
        final float percentFull = (int) (100 * reservations / (float) maxElements);
        return
                " size=" + reservations +
                        " (" + percentFull + "% full)" +
                        " discards=" + discarded +
                        " submitted=" + submitted +
                        " avail=" + avail +
                        " capacity=" + maxElements +
                        " freeVmBytes=" +
                        Runtime.getRuntime().freeMemory();
    }

    protected void debug(Object msg) {
        if (DEBUG) System.out.println(msg);
    }

    protected void info(String msg) {
        System.out.println("** " + msg);
    }

    public void warn(String m, Object... o) {
        LogLog.warn(String.format(m, o) + " " + getStateInfo());
    }

    protected void error(String msg) {
        errorHandler.error(msg);
    }

    // config option
    public void setLocationInfo(boolean locationInfo) {
        this.locationInfo = locationInfo;
    }

    // config option
    public void setMaxElements(int n) {
        this.maxElements = n;
    }

    // config option
    public void setWarningThreshold(int warningThreshold) {
        this.warningThreshold = warningThreshold;
    }

    // config option
    public void setErrorReportIntervalMillis(long n) {
        this.errorReportIntervalMillis = n;
    }

    // config option
    public void setGracefulShutdownTimeMillis(long ms) {
        this.gracefulShutdownTimeMillis = ms;
    }

    // delegate configuration setter to the jdbc appender's config
    public void setUrl(String url) {
        writerThread.appender.setUrl(url);
    }

    // delegate configuration setter to the jdbc appender's config
    public void setUser(String user) {
        writerThread.appender.setUser(user);
    }

    // delegate configuration setter to the jdbc appender's config
    public void setPassword(String password) {
        writerThread.appender.setPassword(password);
    }

    // delegate configuration setter to the jdbc appender's config
    public void setSql(String sql) {
        writerThread.appender.setSql(sql);
    }

    // delegate configuration setter to the jdbc appender's config
    public void setSqlParams(String sqlParams) {
        writerThread.appender.setSqlParams(sqlParams);
    }

    // delegate configuration setter to the jdbc appender's config
    public void setSqlParamsSeparator(String sqlParamsSeparator) {
        writerThread.appender.setSqlParamsSeparator(sqlParamsSeparator);
    }

    // delegate configuration setter to the jdbc appender's config
    public void setDriver(String driver) {
        writerThread.appender.setDriver(driver);
    }

    // delegate configuration setter to the jdbc appender's config
    public void setReconnectTimeMillis(int reconnectTimeMillis) {
        writerThread.appender.setReconnectTimeMillis(reconnectTimeMillis);
    }

}

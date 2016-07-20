package org.exaspace.log4jq.perf;

import java.util.LinkedList;
import java.util.Queue;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Simple appender which queues log events in memory (up to maxElements).
 * Useful for simulation and performance testing. Can be configured
 * to sleep during the append operation to simulate writing to a real
 * output destination.
 */
public class DummyAppender extends AppenderSkeleton {

	// Config value (i.e. that can be configured from log4j framework)
	private long maxElements = 1000;

	// Config value (i.e. that can be configured from log4j framework)
	// - sleep during append() to simulate a slow write action
	// - set to zero or any negative number to do no sleeping
	private long sleepTimeMillis = 0;

	private Queue<LoggingEvent> queue;
	private boolean warned = false;

	public DummyAppender() {
		queue = new LinkedList<LoggingEvent>();
	}

	/*
	* Configuration bean method called by log4j framework - do not remove
	*/
	public long getMaxElements() {
		return maxElements;
	}

	/*
	* Configuration bean method called by log4j framework - do not remove
	*/
	public void setMaxElements(long maxElements) {
		this.maxElements = maxElements;
	}

	/*
	* Configuration bean method called by log4j framework - do not remove
	*/
	public long getSleepTimeMillis() {
		return sleepTimeMillis;
	}

	/*
	* Configuration bean method called by log4j framework - do not remove
	*/
	public void setSleepTimeMillis(long sleepTimeMillis) {
		this.sleepTimeMillis = sleepTimeMillis;
	}

	@Override
	public void activateOptions() {
		LogLog.warn("This appender (" + getClass().getSimpleName() + ") is for test purposes only");
		queue.clear();
	}

	@Override
	public void close() {
		queue.clear();
		warned = false;
	}

	@Override
	public void append(LoggingEvent event) {
		long size = queue.size();
		if (maxElements > 0 && size >= maxElements) {
			if (!warned ) {
				LogLog.warn("Memory queue is full " + size + " (ignoring append)");
				warned = true;
			}
			return;
		}

		queue.add(event);

		if (sleepTimeMillis > 0) {
			try {
				Thread.sleep(sleepTimeMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public boolean requiresLayout() {
		return false;
	}

	// Allow clients to access the internal queue directly. 
	public Queue<LoggingEvent> getQueue() {
		return queue;
	}

}

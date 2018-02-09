package org.exaspace.log4jq.perf;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import java.util.Queue;

/**
 * Simple appender which log events in memory (up to maxElements) into a circular
 * FIFO buffer.
 * 
 * Useful for simulation and performance testing. 
 */
public class CyclicMemoryAppender extends AppenderSkeleton {

	// Config value (i.e. that can be configured from log4j framework)
	private int maxElements = 1000;

	private Queue<LoggingEvent> fifo;

	public CyclicMemoryAppender() {
	}

	@Override
	public void activateOptions() {
		System.err.println("Creating new Cyclic MemoryAppender, maxElements=" + maxElements);
		fifo = new CircularFifoQueue<>(maxElements);
		LogLog.debug("activateOptions. bufsize=" + maxElements);
	}

	@Override
	public void close() {
		if (fifo != null) fifo.clear();
		fifo = null;
	}

	/*
	 * NB AppenderSkeleton has already synchronized on "this" for the append.
	 */
	@Override
	public void append(LoggingEvent event) {
		fifo.add(event);
	}

	@Override
	public boolean requiresLayout() {
		return false;
	}

	// Allow clients to access the internal fifo directly. 
	public Queue<LoggingEvent> getBuffer() {
		return fifo;
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
	public void setMaxElements(int maxElements) {
		this.maxElements = maxElements;
	}
	
}

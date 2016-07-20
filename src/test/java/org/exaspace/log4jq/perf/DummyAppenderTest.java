package org.exaspace.log4jq.perf;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

import java.util.Queue;

import static org.junit.Assert.assertEquals;

public class DummyAppenderTest {

    @Test
    public void shouldStoreLogEventsInMemoryQueue() throws InterruptedException {

        LogManager.resetConfiguration();

        PatternLayout layout = new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN);

        DummyAppender dummyAppender = new DummyAppender();
        dummyAppender.setName("MEM");
        dummyAppender.setLayout(layout);
        dummyAppender.setMaxElements(2);
        dummyAppender.setSleepTimeMillis(0);
        dummyAppender.activateOptions();

        Logger root = Logger.getRootLogger();
        root.addAppender(dummyAppender);
        root.setLevel(Level.DEBUG);

        Logger log = root.getLoggerRepository().getLogger("somelogger");
        log.setLevel(Level.DEBUG);
        log.setAdditivity(false);
        log.addAppender(dummyAppender);

        Queue<LoggingEvent> queue = dummyAppender.getQueue();

        log.info("foo");
        log.info("bar");
        log.info("baz");

        assertEquals("foo", queue.poll().getMessage());
        assertEquals("bar", queue.poll().getMessage());
        assertEquals(0, queue.size());

    }

}
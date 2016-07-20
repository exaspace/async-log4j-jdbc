package org.exaspace.log4jq.perf;

import org.apache.commons.collections.Buffer;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CyclicMemoryAppenderTest {

    @Test
    public void shouldStoreLogEventsInCyclicBuffer() throws InterruptedException {

        LogManager.resetConfiguration();

        PatternLayout layout = new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN);

        CyclicMemoryAppender memoryAppender = new CyclicMemoryAppender();
        memoryAppender.setName("MEM");
        memoryAppender.setLayout(layout);
        memoryAppender.setMaxElements(2);
        memoryAppender.activateOptions();

        Logger root = Logger.getRootLogger();
        root.addAppender(memoryAppender);
        root.setLevel(Level.DEBUG);

        Logger log = root.getLoggerRepository().getLogger("somelogger");
        log.setLevel(Level.DEBUG);
        log.setAdditivity(false);
        log.addAppender(memoryAppender);

        Buffer buffer = memoryAppender.getBuffer();

        log.info("foo");
        log.info("bar");
        log.info("baz");

        assertEquals("bar", ((LoggingEvent) buffer.remove()).getMessage());
        assertEquals("baz", ((LoggingEvent) buffer.remove()).getMessage());
        assertEquals(0, buffer.size());

        for (int i = 0; i <= 10; i++) {
            log.info(String.valueOf(i));
        }
        assertEquals("9", ((LoggingEvent) buffer.remove()).getMessage());
        assertEquals("10", ((LoggingEvent) buffer.remove()).getMessage());
        assertEquals(0, buffer.size());
    }

}
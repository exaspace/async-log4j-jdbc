package org.exaspace.log4jq.perf;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import java.util.LinkedHashMap;
import java.util.Map;

public class RunPerfTest {

    static int LOOPS_PER_THREAD = 100;
    static int NUM_THREADS = 2;
    static long MESSAGE_INTERVAL_MILLIS = 10;
    static int SYSOUT_MODULUS = 100000;

    class LoggerTester implements Runnable {

        private final String loggerName;

        public LoggerTester(String loggerName) {
            this.loggerName = loggerName;
        }

        public void run() {
            long threadId = Thread.currentThread().getId();
            Logger log = Logger.getLogger(loggerName);
            for (int i = 1; i <= LOOPS_PER_THREAD; i++) {
                String msg = log.getName() + "<" + log.hashCode() + "> " +
                        Thread.currentThread().getName() + " "
                        + threadId + " " + i;

                NDC.push("ndc-tid-" + threadId + "-" + i);

                // produce log message
                log.info("info-" + msg);

                if (i % SYSOUT_MODULUS == 0) {
                    report(msg);
                }

                if (MESSAGE_INTERVAL_MILLIS > 0) {
                    try {
                        Thread.sleep(MESSAGE_INTERVAL_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // produce an error log message
                log.error("err-" + msg, new Exception());

                NDC.pop();
            }
            NDC.remove();
        }

    }

    public RunPerfTest() throws Exception {
        Runnable task;
        String[] loggerNames = {
                "DUMMY_INSTANT", // 2000 per second
                "DUMMY_4_MILLIS", // 250 per second
                "CONSOLE", // 1500 per second
                "JDBC_ASYNC",
                "JDBC_SYNC",
                "JDBC_PLUS",
                "JDBC_BUNDLED"
        };
        Map<String, Long> results = new LinkedHashMap<String, Long>();
        for (String loggerName : loggerNames) {
            Logger log = Logger.getLogger(loggerName);
            for (int i = 0; i < 2; i++) {
                log.debug("Initial log message to " + loggerName);
                log.info("Initial log message to " + loggerName);
                log.warn("Initial log message to " + loggerName);
                log.error("Initial log message to " + loggerName);
            }
            long res = -1;
            task = new LoggerTester(loggerName);
            if (NUM_THREADS > 0) {
                res = TaskTimer.timeTasks(NUM_THREADS, task);
            } else {
                task.run();
            }
            if (res != -1) {
                results.put(loggerName, res);
            }
        }
        report("RESULTS:-");
        int msgs = LOOPS_PER_THREAD * NUM_THREADS * 2;
        for (String r : results.keySet()) {
            long time = toMillis(results.get(r));
            float tpm = time / (float) msgs;
            float perSec = tpm > 0 ? 1000 / tpm : 0;
            report("\t" + r + " " + msgs + "msgs " + time + "ms " +
                    tpm + "ms per msg " + perSec + "msgs per sec");
        }
    }

    public static void report(String s) {
        System.out.println(s);
    }

    private static long toMillis(long nanos) {
        return (long) (nanos/1000000.0);
    }

    public static void main(String[] args) throws Exception {
        new RunPerfTest();
        report("END PERF TESTS");
    }

}

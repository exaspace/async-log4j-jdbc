package org.exaspace.log4jq.support;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.config.PropertySetter;

import java.util.Properties;

public class Log4jSupport {

    public static void setupAppender(Class<?> appenderClass, Properties appenderProps) throws Exception {
        Appender appender = createAppender(appenderClass.getName());
        PropertySetter.setProperties(appender, appenderProps, "");
        applyAppenderToRootLogger(appender);
    }

    private static Logger applyAppenderToRootLogger(Appender appender) {
        Logger root = Logger.getRootLogger();
        root.removeAllAppenders();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        return root;
    }

    private static Appender createAppender(String className) throws Exception {
        Appender appender = (Appender) Class.forName(className).newInstance();
        return appender;
    }

}
package org.exaspace.log4jq.support;

import java.sql.Date;

public class LogMessage {
    public long id;
    public Date logdate;
    public String logger;
    public String priority;
    public String threadId;
    public String context;
    public String message;
    public String trace;
}

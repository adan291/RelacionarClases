package org.example.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Custom log formatter that produces readable, structured log output.
 * Format: [YYYY-MM-DD HH:mm:ss.SSS] [LEVEL] [Class.method] - Message
 */
public class AppFormatter extends Formatter {
    
    private static final DateTimeFormatter DATE_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    
    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        
        // Timestamp
        sb.append("[");
        sb.append(DATE_FORMAT.format(record.getInstant()));
        sb.append("] ");
        
        // Level
        sb.append("[");
        sb.append(String.format("%-7s", record.getLevel().getName()));
        sb.append("] ");
        
        // Class and method
        sb.append("[");
        String className = record.getSourceClassName();
        if (className != null) {
            // Use simple class name
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                className = className.substring(lastDot + 1);
            }
        } else {
            className = record.getLoggerName();
        }
        sb.append(className);
        
        String methodName = record.getSourceMethodName();
        if (methodName != null) {
            sb.append(".");
            sb.append(methodName);
        }
        sb.append("] ");
        
        // Message
        sb.append("- ");
        sb.append(formatMessage(record));
        sb.append(System.lineSeparator());
        
        // Exception stack trace if present
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            thrown.printStackTrace(pw);
            sb.append(sw.toString());
        }
        
        return sb.toString();
    }
}

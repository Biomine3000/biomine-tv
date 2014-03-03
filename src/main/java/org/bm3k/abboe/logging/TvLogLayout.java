package org.bm3k.abboe.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import util.DateUtils;

public class TvLogLayout extends LayoutBase<ILoggingEvent> {
    private DateUtils.BMZGenerator gen = new DateUtils.BMZGenerator();
    private TargetLengthBasedClassNameAbbreviator abbr = new TargetLengthBasedClassNameAbbreviator(5);

    @Override
    public String doLayout(ILoggingEvent event) {
        StringBuilder buf = new StringBuilder(128);

        // %d{HH:mm:ss.SSS} [%thread] %-5level %logger{5} - %msg%n
        buf.append(gen.generate());
        buf.append(" ");
        buf.append(String.format("%-6s", event.getLevel()));
        if((event.getLevel() == Level.ERROR || event.getLevel() == Level.WARN)
                && event.getThrowableProxy() !=null) {
            buf.append(" - ");
            buf.append(
                    StringUtils.substringBefore(event.getThrowableProxy().getMessage(),
                            IOUtils.LINE_SEPARATOR));
        }

        buf.append(" ");
        buf.append(abbr.abbreviate(event.getLoggerName()));

        buf.append(" - ");
        buf.append(event.getFormattedMessage());
        buf.append(CoreConstants.LINE_SEPARATOR);

        return buf.toString();
    }

}

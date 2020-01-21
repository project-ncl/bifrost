package org.jboss.pnc.bifrost.mock;

import org.jboss.pnc.bifrost.source.dto.Line;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class LineProducer {

    public static Line getLine(Integer lineNumber, boolean last, String ctx) {
        return getLine(lineNumber, last, ctx, "org.jboss.pnc._userlog_");
    }

    public static Line getLine(Integer lineNumber, boolean last, String ctx, String loggerName) {
        return Line.newBuilder()
                .id(UUID.randomUUID().toString())
                .timestamp(Long.toString(System.currentTimeMillis()))
                .logger(loggerName)
                .message("Message " + lineNumber)
                .last(last)
                .ctx(ctx)
                .tmp(false)
                .exp(null)
                .build();
    }

    public static List<Line> getLines(Integer numberOfLines, String ctx) {
        return getLines(numberOfLines, ctx, "org.jboss.pnc._userlog_");
    }

    public static List<Line> getLines(Integer numberOfLines, String ctx, String loggerName) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < numberOfLines; i++) {
            boolean last = i == numberOfLines - 1;
            lines.add(getLine(i, last, ctx, loggerName));
        }
        return lines;
    }
}

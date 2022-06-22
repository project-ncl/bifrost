package org.jboss.pnc.bifrost.endpoint;

import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.vertx.core.impl.ConcurrentHashSet;
import org.jboss.logging.Logger;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.common.DateUtil;
import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.bifrost.source.db.LogLevel;
import org.jboss.pnc.bifrost.source.db.LogLine;
import org.jboss.pnc.common.concurrent.Sequence;
import org.jboss.pnc.common.pnc.LongBase32IdConverter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.toList;

@ApplicationScoped
public class MrBean {
    private static final Logger logger = Logger.getLogger(MrBean.class);

    @Inject
    RestImpl bifrost;

    @Inject
    TransactionManager manager;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @TransactionConfiguration(timeout = 3600)
    public void queryAndSave(String logContextQuery, String prefixFilters, int maxLines) throws IOException, SystemException {
        logger.info("Transaction START:" + manager.getTransaction().toString());
        Line afterLine = null;
        List<Line> lines = null;
        Set<LogEntry> persistedEntries = new HashSet<>();
        do {
            lines = bifrost.getLines(
                    "mdc.processContext.keyword:" + logContextQuery,
                    prefixFilters,
                    afterLine,
                    Direction.ASC,
                    maxLines);
            if (!lines.isEmpty()) {
                afterLine = lines.get(lines.size() - 1);
            }
            Map<LogEntry, List<LogLine>> groupedLines = lines.stream()
                    .collect(groupingBy(this::extractLogEntry, mapping(this::toLine, toList())));

            // persist Entries before using them as foreign keys in Lines
            for (LogEntry entry : groupedLines.keySet()) {
                logger.info("Found logEntry: " + entry.toString());
                if (!persistedEntries.contains(entry)) {
                    persistedEntries.add(entry);
                    entry.setId(Sequence.nextId());
                    entry.persist();
                }
            }
            logger.info("Persisting");
            LogEntry.flush();
            logger.info("Persisted");
            for (var group : groupedLines.entrySet()) {
                var entry = group.getKey();
                var lineList = group.getValue()
                        .stream()
                        // avoid duplicate log lines
                        .distinct()
                        .collect(toList());
                if (persistedEntries.contains(entry)) {
                    LogEntry finalEntry = entry;
                    entry = persistedEntries.stream()
                            // find the hibernate managed Entry
                            .filter(finalEntry::equals)
                            .findFirst()
                            .get();
                }
                for (LogLine line : lineList) {
                    line.setLogEntry(entry);
                    line.setId(Sequence.nextId());
                    line.persist();
                }
                logger.info("PERSISTED LINES size(" + lineList.size() + ")");
            }
            logger.info("ITERATION COMPLETED. SAVED ENTRIES: " + persistedEntries.toString());
        } while (lines.size() == maxLines);
        logger.info("Transaction END:" + manager.getTransaction().toString());
    }

    private LogEntry extractLogEntry(Line line) {
        long processContext;
        String processContextVariant = line.getMdc().get("processContextVariant");
        String requestContext = line.getMdc().get("requestContext");
        Boolean temporary = null;
        Long buildId = null;

        try {
            String processContextString = line.getMdc().get("processContext");
            if (processContextString.startsWith("build-")) {
                processContextString = processContextString.replace("build-", "");
                processContext = LongBase32IdConverter.toLong(processContextString);
            } else {
                processContext = Long.parseLong(processContextString);
            }
        } catch (NumberFormatException e) {
            logger.error("cant parse processContext for line " + line.toString(), e);
            return null;
        }

        // if temporary is not present leave null otherwise parse
        String temp = line.getMdc().get("temporary");
        if (temp != null) {
            temporary = Boolean.parseBoolean(temp);
        }

        try {
            buildId = Long.parseLong(line.getMdc().get("buildId"));
        } catch (NumberFormatException e) {
            try {
                buildId = LongBase32IdConverter.toLong(line.getMdc().get("buildId"));
            } catch (Exception ex) {
                // leave null
            }
        }

        LogEntry logEntry = new LogEntry();
        logEntry.setProcessContext(processContext);
        logEntry.setProcessContextVariant(processContextVariant);
        logEntry.setRequestContext(requestContext);
        logEntry.setBuildId(buildId);
        logEntry.setTemporary(temporary);

        return logEntry;
    }

    private LogLine toLine(Line line) {
        LogLine logLine = new LogLine();

        logLine.setLine(line.getMessage());
        logLine.setLoggerName(line.getLoggerName());
        logLine.setEventTimestamp(OffsetDateTime.parse(DateUtil.validateAndFixInputDate(line.getTimestamp())));
        try {
            logLine.setSequence(Integer.parseInt(line.getSequence()));
        } catch (Exception e) {
            // sequence is null
        }
        try {
            logLine.setLevel(LogLevel.valueOf(line.getLogLevel()));
        } catch (Exception e) {
            // log level is null
        }

        return logLine;
    }

}

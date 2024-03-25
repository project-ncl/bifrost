package org.jboss.pnc.bifrost.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.engine.jdbc.BlobProxy;
import org.jboss.pnc.bifrost.source.db.FinalLog;
import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.common.Random;
import org.jboss.pnc.common.concurrent.Sequence;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@QuarkusTest
public class FinalLogTest {

    /**
     * Testing if we filter out multiple entries for a particular process context, tag and loggername
     *
     * @throws Exception kaboom!
     */
    @Test
    @Transactional
    public void shouldGetFinalLog() throws Exception {
        long processContext = 1323123L;
        LogEntry logEntryFirst = createLogEntry(processContext, "0");
        LogEntry logEntrySecond = createLogEntry(processContext, "1");
        LogEntry logEntryThird = createLogEntry(processContext, "2");

        FinalLog firstFailedLog = createFinalLog("hello", logEntryFirst, "a", "alignment");
        FinalLog secondFailedLog = createFinalLog("hello 2", logEntrySecond, "a", "alignment");
        FinalLog thirdSuccessfulLog = createFinalLog("hello 3", logEntryThird, "a", "alignment");
        FinalLog fourthLogWIthDifferentTag = createFinalLog("hello 4", logEntryThird, "b", "build-log");

        Collection<FinalLog> logs = FinalLog.getFinalLogsWithoutPreviousRetries(processContext, "alignment");

        Assertions.assertEquals(logs.size(), 1);

        FinalLog logFetched = logs.stream().findFirst().get();

        Assertions.assertEquals(logFetched.id, thirdSuccessfulLog.id);

        // verify logs are the same
        String logLine = new String(logFetched.logContent.getBinaryStream().readAllBytes());
        Assertions.assertEquals(logLine, "hello 3");
    }

    @Test
    @Transactional
    void testMultipleEntriesForTagListedProperly() throws Exception {
        long processContext = 1323123L;
        LogEntry logEntry = createLogEntry(processContext, "0");

        FinalLog firstLog = createFinalLog("hello", logEntry, "a", "build");
        FinalLog secondLog = createFinalLog("hello 2", logEntry, "b", "build");

        Collection<FinalLog> logs = FinalLog.getFinalLogsWithoutPreviousRetries(processContext, "build");

        Assertions.assertEquals(logs.size(), 2);
        FinalLog[] fetchedLogsArray = logs.toArray(new FinalLog[0]);

        String logLineFirst = new String(fetchedLogsArray[0].logContent.getBinaryStream().readAllBytes());
        String logLineSecond = new String(fetchedLogsArray[1].logContent.getBinaryStream().readAllBytes());

        Assertions.assertEquals("hello", logLineFirst);
        Assertions.assertEquals("hello 2", logLineSecond);
    }

    private static LogEntry createLogEntry(long processContext, String processContextVariant) {
        LogEntry logEntry = new LogEntry();
        logEntry.setId(Sequence.nextId());
        logEntry.setProcessContext(processContext);
        logEntry.setProcessContextVariant(processContextVariant);

        logEntry.persist();

        return logEntry;
    }

    private static FinalLog createFinalLog(String text, LogEntry logEntry, String loggerName, String tag) {
        FinalLog finalLog = new FinalLog();
        finalLog.id = Sequence.nextId();
        finalLog.logContent = BlobProxy.generateProxy(text.getBytes(StandardCharsets.UTF_8));
        finalLog.logEntry = logEntry;
        finalLog.eventTimestamp = OffsetDateTime.now();
        finalLog.loggerName = loggerName;
        finalLog.md5sum = "a";
        finalLog.tags = List.of(tag);

        finalLog.persist();

        return finalLog;
    }

}

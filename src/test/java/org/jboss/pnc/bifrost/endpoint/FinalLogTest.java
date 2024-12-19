package org.jboss.pnc.bifrost.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.engine.jdbc.BlobProxy;
import org.jboss.pnc.bifrost.source.db.FinalLog;
import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.common.concurrent.Sequence;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Set;

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

        Assertions.assertEquals(1, logs.size());

        FinalLog logFetched = logs.stream().findFirst().get();

        Assertions.assertEquals(logFetched.id, thirdSuccessfulLog.id);

        // verify logs are the same
        String logLine = new String(logFetched.logContent.getBinaryStream().readAllBytes());
        Assertions.assertEquals("hello 3", logLine);
    }

    @Test
    @Transactional
    void testMultipleEntriesForTagListedProperly() throws Exception {
        long processContext = 1323123L;
        LogEntry logEntry = createLogEntry(processContext, "0");

        FinalLog firstLog = createFinalLog("hello", logEntry, "a", "build");
        FinalLog secondLog = createFinalLog("hello 2", logEntry, "b", "build");

        Collection<FinalLog> logs = FinalLog.getFinalLogsWithoutPreviousRetries(processContext, "build");

        Assertions.assertEquals(2, logs.size());
        FinalLog[] fetchedLogsArray = logs.toArray(new FinalLog[0]);

        String logLineFirst = new String(fetchedLogsArray[0].logContent.getBinaryStream().readAllBytes());
        String logLineSecond = new String(fetchedLogsArray[1].logContent.getBinaryStream().readAllBytes());

        Assertions.assertEquals("hello", logLineFirst);
        Assertions.assertEquals("hello 2", logLineSecond);
    }

    @Test
    @Transactional
    void testDeleteAll() throws Exception {
        long processContext = 1323124L;
        LogEntry logEntry = createLogEntry(processContext, "0");

        FinalLog firstLog = createFinalLog("hello", logEntry, "a", "build");
        FinalLog secondLog = createFinalLog("hello 2", logEntry, "b", "build");
        FinalLog thirdLog = createFinalLog("hello 3", logEntry, "c", "alignment");

        long deleted = FinalLog.deleteByProcessContext(processContext, null, false);

        Assertions.assertEquals(3, deleted);
    }

    @Test
    @Transactional
    void testDeleteTag() throws Exception {
        long processContext = 1323125L;
        LogEntry logEntry = createLogEntry(processContext, "0");

        FinalLog firstLog = createFinalLog("hello", logEntry, "a", "build");
        FinalLog secondLog = createFinalLog("hello 2", logEntry, "b", "build");
        FinalLog thirdLog = createFinalLog("hello 3", logEntry, "c", "alignment");

        long deleted = FinalLog.deleteByProcessContext(processContext, "build", false);

        Assertions.assertEquals(2, deleted);
    }

    @Test
    @Transactional
    void shouldNotDeletePersistent() throws Exception {
        long processContext = 1323126L;
        LogEntry logEntry = createLogEntry(processContext, "0");

        FinalLog firstLog = createFinalLog("hello", logEntry, "a", "build");
        FinalLog secondLog = createFinalLog("hello 2", logEntry, "b", "build");
        FinalLog thirdLog = createFinalLog("hello 3", logEntry, "c", "alignment");

        long deleted = FinalLog.deleteByProcessContext(processContext, null, true);

        Assertions.assertEquals(0, deleted);
    }

    @Test
    @Transactional
    void shouldDeleteTemporary() throws Exception {
        long processContext = 1323127L;
        LogEntry logEntry = createLogEntry(processContext, "0", true);
        LogEntry logEntry2 = createLogEntry(processContext, "0", false);

        FinalLog firstLog = createFinalLog("hello", logEntry, "a", "build");
        FinalLog secondLog = createFinalLog("hello 2", logEntry, "b", "build");
        FinalLog thirdLog = createFinalLog("hello 3", logEntry, "c", "alignment");

        FinalLog firstLog2 = createFinalLog("hello", logEntry2, "a", "build");
        FinalLog secondLog2 = createFinalLog("hello 2", logEntry2, "b", "build");
        FinalLog thirdLog2 = createFinalLog("hello 3", logEntry2, "c", "alignment");

        long deleted = FinalLog.deleteByProcessContext(processContext, null, true);

        Assertions.assertEquals(3, deleted);
    }

    private static LogEntry createLogEntry(long processContext, String processContextVariant) {
        return createLogEntry(processContext, processContextVariant, false);
    }

    private static LogEntry createLogEntry(long processContext, String processContextVariant, boolean temporary) {
        LogEntry logEntry = new LogEntry();
        logEntry.setId(Sequence.nextId());
        logEntry.setProcessContext(processContext);
        logEntry.setProcessContextVariant(processContextVariant);
        logEntry.setTemporary(temporary);
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
        finalLog.tags = Set.of(tag);

        finalLog.persist();

        return finalLog;
    }

}

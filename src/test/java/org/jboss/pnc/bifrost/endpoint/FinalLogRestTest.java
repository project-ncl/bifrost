package org.jboss.pnc.bifrost.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.engine.jdbc.BlobProxy;
import org.jboss.pnc.bifrost.source.db.FinalLog;
import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.common.concurrent.Sequence;
import org.jboss.pnc.common.pnc.LongBase32IdConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class FinalLogRestTest {

    private static final String text = "hahaha";
    private static final long processContext = Sequence.nextId();

    @BeforeEach
    @Transactional
    public void insert() {
        LogEntry logEntry = createLogEntry(processContext, "1");
        FinalLog finalLog = createFinalLog(text, logEntry, "loggername", "build-log");
    }

    @Test
    public void emptyFinalLogShouldReturn404() {
        given().when().get("/final-log/1234/build-log").then().statusCode(404);
    }

    @Test
    public void finalLogShouldReturn200() {
        String sequenceString = LongBase32IdConverter.toString(processContext);

        given().when().get("/final-log/" + sequenceString + "/build-log").then().statusCode(200).body(is(text));
        given().when()
                .get("/final-log/" + sequenceString + "/build-log/size")
                .then()
                .statusCode(200)
                .body(is("" + text.length()));
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
        finalLog.size = text.length();
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

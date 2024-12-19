package org.jboss.pnc.bifrost.source.db;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.pnc.common.Random;
import org.jboss.pnc.common.concurrent.Sequence;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LogEntryTest {

    @Test
    @Transactional
    void findExisting() {
        long processContext = Random.randInt(0, 10000);
        String processContextVariant = "0";
        LogEntry logEntry = new LogEntry(Sequence.nextId(), processContext, processContextVariant, null, false, null);
        logEntry.persist();

        LogEntry toFind = new LogEntry(0L, processContext, processContextVariant, null, false, null);

        Optional<LogEntry> existing = LogEntry.findExisting(toFind);
        Assertions.assertTrue(existing.isPresent());
    }
}
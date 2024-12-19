package org.jboss.pnc.bifrost.source.db;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.pnc.common.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LogEntryDbRepositoryTest {

    @Inject
    LogEntryDbRepository logEntryDbRepository;

    @Test
    @Transactional
    void get() {

        // First: let's explicitly create a logentry with id 0
        long processContext = Random.randInt(0, 10000);
        String processContextVariant = "0";
        LogEntry logEntry = new LogEntry();
        logEntry.id = 0L;
        logEntry.setProcessContext(processContext);
        logEntry.setProcessContextVariant(processContextVariant);
        logEntry.setTemporary(false);
        logEntry.persist();

        // Second, let's create a second, unrelated logentry where the id is not set
        long processContextSecond = Random.randInt(0, 10000);
        LogEntry logEntryToSearch = new LogEntry();
        logEntryToSearch.setProcessContext(processContextSecond);
        logEntryToSearch.setProcessContextVariant(processContextVariant);
        logEntryToSearch.setTemporary(false);

        // search or insert it. Hopefully not Exception is thrown because the id of the second log is not set
        LogEntry found = logEntryDbRepository.get(logEntryToSearch);
        Assertions.assertEquals(processContextSecond, found.getProcessContext());
    }
}
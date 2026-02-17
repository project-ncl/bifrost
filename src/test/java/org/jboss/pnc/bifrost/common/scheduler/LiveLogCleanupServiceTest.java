/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020-2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.bifrost.common.scheduler;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.bifrost.source.db.LogLine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

@QuarkusTest
public class LiveLogCleanupServiceTest {

    @Inject
    LiveLogCleanupService cleanupService;

    @Test
    void testSchedulerDeletesOnlyFinalizedLogLines() {
        QuarkusTransaction.requiringNew().run(() -> {
            LogEntry logEntry = new LogEntry(1L, 100L, null, null, false, null);
            logEntry.persist();

            OffsetDateTime oldTimestamp = OffsetDateTime.now().minusMonths(4);
            OffsetDateTime evenOlderTimestamp = OffsetDateTime.now().minusMonths(6);

            new LogLine(10L, logEntry, oldTimestamp, 1L, null, "test.logger", "delete 1").persist();
            new LogLine(11L, logEntry, oldTimestamp, 2L, null, "test.logger", "delete 2").persist();
            new LogLine(12L, logEntry, OffsetDateTime.now(), 3L, null, "test.logger", "keep me").persist();
            new LogLine(13L, logEntry, evenOlderTimestamp, 4L, null, "different.logger", "keep me").persist();
        });

        cleanupService.cleanupLiveLogs();

        Assertions.assertNull(LogLine.findById(10L));
        Assertions.assertNull(LogLine.findById(11L));
        Assertions.assertNotNull(LogLine.findById(12L));
        Assertions.assertNotNull(LogLine.findById(13L));

        // cleanup
        QuarkusTransaction.requiringNew().run(() -> {
            LogLine.delete("id = ?1", 12L);
            LogLine.delete("id = ?1", 13L);
        });
    }
}

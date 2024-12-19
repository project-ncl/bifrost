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
package org.jboss.pnc.bifrost.test;

import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.bifrost.source.db.LogEntryLocalRepository;
import org.jboss.pnc.bifrost.source.db.LogLevel;
import org.jboss.pnc.bifrost.source.db.LogLine;
import org.jboss.pnc.common.concurrent.Sequence;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Dependent
public class DbUtils {

    @Inject
    LogEntryLocalRepository logEntryRepository;

    public void insertLines(Integer numberOfLines, long ctx, String loggerName) throws Exception {
        insertLines(numberOfLines, ctx, loggerName, null);
    }

    @Transactional
    public void insertLines(Integer numberOfLines, long ctx, String loggerName, OffsetDateTime timestamp)
            throws Exception {
        LogEntry logEntry = new LogEntry(Sequence.nextId(), ctx, "0", "abc123", false, 1L);
        for (int id = 0; id < numberOfLines; id++) {
            LogLine logLine = new LogLine(
                    Sequence.nextId(),
                    logEntryRepository.get(logEntry),
                    Optional.ofNullable(timestamp).orElse(OffsetDateTime.now()),
                    id,
                    LogLevel.INFO,
                    loggerName,
                    "My message " + id);
            logLine.persist();
        }
    }

}

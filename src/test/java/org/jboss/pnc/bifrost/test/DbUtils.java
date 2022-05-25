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

import org.jboss.pnc.bifrost.source.db.LogLevel;
import org.jboss.pnc.bifrost.source.db.LogRecord;
import org.jboss.pnc.common.concurrent.Sequence;

import javax.enterprise.context.Dependent;
import javax.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Dependent
public class DbUtils {

    public void insertLine(Integer numberOfLines, long ctx, String loggerName) throws Exception {
        insertLine(numberOfLines, ctx, loggerName, null);
    }

    @Transactional
    public void insertLine(Integer numberOfLines, long ctx, String loggerName, Instant timestamp) throws Exception {
        for (int id = 0; id < numberOfLines; id++) {
            LogRecord logRecord = new LogRecord(
                    Sequence.nextId(),
                    Optional.ofNullable(timestamp).orElse(Instant.now()),
                    id,
                    LogLevel.INFO,
                    loggerName,
                    "My message " + id,
                    ctx,
                    0L,
                    "abc123",
                    false,
                    1L);
            logRecord.persist();
        }
    }

}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.bifrost.kafkaconsumer;

import org.jboss.pnc.bifrost.source.db.LogLevel;
import org.jboss.pnc.bifrost.source.db.LogRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class FilterTest {

    @Test
    public void shouldFilterLogs() {
        List<Configuration.LogFilter> anyMatch = new ArrayList<>();
        anyMatch.add(logFilter("org.jboss", LogLevel.valueOf("DEBUG")));
        anyMatch.add(logFilter("org.apache", LogLevel.valueOf("INFO")));
        Filter filter = new Filter(anyMatch);

        Assertions.assertTrue(filter.match(simpleLogRecord("org.jboss", LogLevel.ERROR)));
        Assertions.assertTrue(filter.match(simpleLogRecord("org.jboss", LogLevel.WARN)));
        Assertions.assertTrue(filter.match(simpleLogRecord("org.jboss.pnc", LogLevel.WARN)));
        Assertions.assertTrue(filter.match(simpleLogRecord("org.jboss.pnc", LogLevel.INFO)));
        Assertions.assertTrue(filter.match(simpleLogRecord("org.jboss.pnc", LogLevel.DEBUG)));
        Assertions.assertFalse(filter.match(simpleLogRecord("org.jboss.pnc", LogLevel.TRACE)));

        Assertions.assertTrue(filter.match(simpleLogRecord("org.apache", LogLevel.INFO)));
        Assertions.assertFalse(filter.match(simpleLogRecord("org.apache", LogLevel.DEBUG)));

        Assertions.assertFalse(filter.match(simpleLogRecord("io.quarkus", LogLevel.ERROR)));
    }

    private LogRecord simpleLogRecord(String loggerName, LogLevel level) {
        LogRecord record = new LogRecord();
        record.setLoggerName(loggerName);
        record.setLevel(level);
        return record;
    }

    static Configuration.LogFilter logFilter(String loggerNamePrefix, LogLevel level) {
        return new Configuration.LogFilter() {
            @Override
            public String loggerNamePrefix() {
                return loggerNamePrefix;
            }

            @Override
            public LogLevel level() {
                return level;
            }
        };
    }
}

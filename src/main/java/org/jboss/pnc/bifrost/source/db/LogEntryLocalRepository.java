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
package org.jboss.pnc.bifrost.source.db;

import io.quarkus.arc.Priority;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Alternative
@Priority(10)
@ApplicationScoped
public class LogEntryLocalRepository implements LogEntryRepository {

    private Map<LogEntry, LogEntry> cache = new HashMap<>();

    /**
     * Return persisted log entry. If logEntry is not cached yet, it is persisted and then returned. There is
     * intentionally no DB look-up and duplicates are acceptable to avoid a select before each insert of non-existing
     * entries.
     */
    @Override
    @Transactional
    public LogEntry get(LogEntry logEntry) {
        return cache.computeIfAbsent(logEntry, e -> {
            logEntry.persist();
            return logEntry;
        });
    }
}

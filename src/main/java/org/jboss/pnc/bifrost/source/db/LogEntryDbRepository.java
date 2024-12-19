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

import org.jboss.pnc.common.Strings;
import org.jboss.pnc.common.concurrent.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.transaction.Transactional;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Alternative
@Priority(20)
@ApplicationScoped
public class LogEntryDbRepository implements LogEntryRepository {

    private static final Logger LOG = LoggerFactory.getLogger(LogEntryDbRepository.class);

    /**
     * Return an already persisted log entry if it exists otherwise persists it. Checking for existing logEntry is using
     * cached query to avoid a select before each insert. Query cache may not be synchronized between all instances in
     * the cluster and may cause duplicates in the database, duplicates are acceptable in favor of performance.
     */
    @Transactional
    public LogEntry get(LogEntry logEntry) {
        // Avoid duplicated logEntry values with empty and 0
        if (Strings.isEmpty(logEntry.processContextVariant)) {
            logEntry.setProcessContextVariant("0");
        }
        return LogEntry.findExisting(logEntry).orElseGet(() -> {
            LOG.debug("Persisting LogEntry: " + logEntry);
            // set a proper id since it's probably not set when we are trying to search for an existing log entry
            logEntry.id = Sequence.nextId();
            logEntry.persist();
            return logEntry;
        });
    }
}

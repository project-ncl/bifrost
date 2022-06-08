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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.Blocking;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.jboss.pnc.bifrost.source.db.LogEntryRepository;
import org.jboss.pnc.bifrost.source.db.LogLine;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

/**
 * Consume from a Kafka topic, parse the data and store it in the database
 */
@ApplicationScoped
@Slf4j
public class MessageConsumer {

    private static final String className = MessageConsumer.class.getName();

    private final Logger logger = Logger.getLogger(MessageConsumer.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    MeterRegistry registry;

    @Inject
    Configuration configuration;

    @Inject
    StoredCounter storedCounter;

    @Inject
    private LogEntryRepository logEntryRepository;

    Filter acceptFilter;

    private Counter errCounter;

    @PostConstruct
    void initMetrics() {
        errCounter = registry.counter(className + ".error.count");
        acceptFilter = new Filter(configuration.acceptFilters());
    }

    /**
     * Consume messages from a Kafka topic and store them to the database.
     */
    @Timed
    @Blocking
    @Incoming("logs")
    @Transactional
    public void consume(String json) {
        try {
            LogLine logLine = mapper.readValue(json, LogLine.class);
            if (log.isTraceEnabled()) {
                log.trace(logLine.toString());
            }
            if (acceptFilter.match(logLine)) {
                logLine.setLogEntry(logEntryRepository.get(logLine.getLogEntry()));
                logLine.persistAndFlush();
                storedCounter.increment();
            }
        } catch (Exception e) {
            errCounter.increment();
            log.error("Error while saving data", e);
        }
    }

}

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
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.PostConstruct;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.pnc.bifrost.source.db.LogEntryRepository;
import org.jboss.pnc.bifrost.source.db.LogLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;

import java.util.List;

import static org.jboss.pnc.bifrost.common.Strings.sanitize;

/**
 * Consume from a Kafka topic, parse the data and store it in the database
 */
@ApplicationScoped
public class MessageConsumer {

    private static final String className = MessageConsumer.class.getName();

    private final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    MeterRegistry registry;

    @Inject
    Configuration configuration;

    @Inject
    StoredCounter storedCounter;

    @Inject
    LogEntryRepository logEntryRepository;

    AcceptFilter acceptFilter;

    DenyFilter denyFilter;

    private Counter errCounter;

    private Counter micrometerStoredCounter;

    @PostConstruct
    void initMetrics() {
        errCounter = registry.counter(className + ".error.count");
        micrometerStoredCounter = registry.counter(className + ".messages-ingested");
        acceptFilter = new AcceptFilter(configuration.acceptFilters());
        denyFilter = new DenyFilter(configuration.denyFilters());
    }

    /**
     * Consume messages from a Kafka topic and store them to the database.
     */
    @Timed
    @Blocking
    @Incoming("logs")
    @Transactional
    @WithSpan()
    public void consume(@SpanAttribute(value = "json") List<String> batchJsonLines) {
        logger.info("Received {} messages per batch", batchJsonLines.size());

        for (String json : batchJsonLines) {
            consumeSingleMessage(json);
        }
    }

    private void consumeSingleMessage(String json) {
        logger.debug("Received json line: " + json);
        try {
            LogLine logLine = mapper.readValue(json, LogLine.class);
            logger.debug("Received line: " + logLine.toString());

            // If logLine matches a deny filter, discard silently with no processing
            if (denyFilter.match(logLine)) {
                logger.debug("Log line matches deny filter: " + logLine.toString());
                return;
            }

            if (logLine.getLogEntry() != null && logLine.getLogEntry().getProcessContext() == null) {
                logger.warn("Skipping log line due to null processContext. Line: " + logLine);
                return;
            }
            if (acceptFilter.match(logLine)) {
                try {
                    logLine.setLine(sanitize(logLine.getLine()));
                    logLine.setLogEntry(logEntryRepository.get(logLine.getLogEntry()));
                    logLine.persist();
                    storedCounter.increment();
                    micrometerStoredCounter.increment();
                } catch (ConstraintViolationException e) {
                    logger.warn("Skipping log line due to: " + e.getMessage() + ". Line: " + logLine);
                }
            }
        } catch (Exception e) {
            errCounter.increment();
            logger.error("Error while reading and saving the data", e);
            throw new RuntimeException(e);
        }
    }
}

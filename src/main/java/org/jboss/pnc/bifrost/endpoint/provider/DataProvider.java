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
package org.jboss.pnc.bifrost.endpoint.provider;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jboss.logging.Logger;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.Config;
import org.jboss.pnc.bifrost.common.Produced;
import org.jboss.pnc.bifrost.common.Reference;
import org.jboss.pnc.bifrost.common.scheduler.BackOffRunnableConfig;
import org.jboss.pnc.bifrost.common.scheduler.Subscription;
import org.jboss.pnc.bifrost.common.scheduler.Subscriptions;
import org.jboss.pnc.bifrost.source.Source;
import org.jboss.pnc.common.Strings;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
// @MainBean
@ApplicationScoped
public class DataProvider {

    private static final String className = DataProvider.class.getName();

    private Logger logger = Logger.getLogger(DataProvider.class);

    @Inject
    BackOffRunnableConfig backOffRunnableConfig;

    @Inject
    Config config;

    @Inject
    Subscriptions subscriptions;

    @Produced
    @Inject
    Source source;

    @Inject
    MeterRegistry registry;

    private Counter errCounter;

    void initMetrics() {
        errCounter = registry.counter(className + ".error.count");
    }

    @PostConstruct
    public void init() {
        initMetrics();
    }

    public void unsubscribe(Subscription subscription) {
        subscriptions.unsubscribe(subscription);
    }

    @Timed
    public void subscribe(
            String matchFilters,
            String prefixFilters,
            Optional<Line> afterLine,
            Consumer<Line> onLine,
            Subscription subscription,
            Optional<Integer> maxLines) {

        final int[] fetchedLines = { 0 };

        Consumer<Subscriptions.TaskParameters<Line>> searchTask = (parameters) -> {
            Optional<Line> lastResult = Optional.ofNullable(parameters.getLastResult());
            Consumer<Line> onLineInternal = line -> {
                if (line != null) {
                    fetchedLines[0]++;
                }
                parameters.getResultConsumer().accept(line);
            };
            try {
                logger.debug(
                        "Reading from source, subscription " + subscription + " already fetched " + fetchedLines[0]
                                + " lines.");
                readFromSource(
                        matchFilters,
                        prefixFilters,
                        getFetchSize(fetchedLines[0], maxLines),
                        lastResult,
                        onLineInternal);
                logger.debug(
                        "Read from source completed, subscription " + subscription + " fetched lines: "
                                + fetchedLines[0]);
            } catch (Exception e) {
                errCounter.increment();
                logger.error("Error getting data from Elasticsearch.", e);
                subscriptions.unsubscribe(subscription, Subscriptions.UnsubscribeReason.NO_DATA_FROM_SOURCE);
            }
        };

        subscriptions.subscribe(subscription, searchTask, afterLine, onLine, backOffRunnableConfig);
    }

    /**
     * Example filters:
     *
     * "prefixFilters": loggerName.keyword: org.jboss.pnc.causeway|org.jboss.pnc._userlog_,
     * level:INFO|ERROR|WARN, "matchFilters": mdc.buildId:317211334116737472,
     * mdc.processContext:317211334116737024
     */
    protected void readFromSource(
            String matchFilters,
            String prefixFilters,
            int fetchSize,
            Optional<Line> lastResult,
            Consumer<Line> onLine) throws IOException {
        source.get(
                Strings.toMap(matchFilters),
                Strings.toMap(prefixFilters),
                lastResult,
                Direction.ASC,
                fetchSize,
                onLine);
    }

    /**
     * Blocking call, <code>onLine<code/> is called in the calling thread.
     */
    @Timed
    public void get(
            String matchFilters,
            String prefixFilters,
            Optional<Line> afterLine,
            Direction direction,
            Optional<Integer> maxLines,
            Consumer<Line> onLine) throws IOException {

        final Reference<Line> lastLine;
        if (afterLine.isPresent()) {
            // Make sure line is marked as last. Being non last will result in endless loop in case of no results.
            lastLine = new Reference<>(afterLine.get().cloneBuilder().last(true).build());
        } else {
            lastLine = new Reference<>();
        }

        final int[] fetchedLines = { 0 };
        Consumer<Line> onLineInternal = line -> {
            if (line != null) {
                fetchedLines[0]++;
                onLine.accept(line);
            }
            lastLine.set(line);
        };
        do {
            int fetchSize = getFetchSize(fetchedLines[0], maxLines);
            if (fetchSize < 1) {
                break;
            }
            source.get(
                    Strings.toMap(matchFilters),
                    Strings.toMap(prefixFilters),
                    Optional.ofNullable(lastLine.get()),
                    direction,
                    fetchSize,
                    onLineInternal);
        } while (lastLine.get() != null && !lastLine.get().isLast());
    }

    private int getFetchSize(int fetchedLines, Optional<Integer> maxLines) {
        int defaultFetchSize = config.getDefaultSourceFetchSize();
        if (maxLines.isPresent()) {
            int max = maxLines.get();
            if (fetchedLines + defaultFetchSize > max) {
                return max - fetchedLines;
            }
        }
        return defaultFetchSize;
    }

}

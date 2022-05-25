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
package org.jboss.pnc.bifrost.source;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.source.db.DatabaseSource;
import org.jboss.pnc.bifrost.source.db.LogRecord;
import org.jboss.pnc.bifrost.test.DbUtils;
import org.jboss.pnc.bifrost.test.Wait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
public class DatabaseSourceTest {

    private static Logger logger = Logger.getLogger(DatabaseSourceTest.class);

    private static final String DEFAULT_LOGGER = "org.jboss.pnc._userlog_";

    @Inject
    DbUtils dbUtils;

    @Inject
    DatabaseSource databaseSource;

    @BeforeEach
    @Transactional
    public void init() throws Exception {
        // clean up the DB
        LogRecord.deleteAll();
    }

    @Test
    public void shouldReadData() throws Exception {
        dbUtils.insertLine(10, 1, DEFAULT_LOGGER);
        Map<String, List<String>> noFilters = Collections.emptyMap();

        List<Line> receivedLines = new ArrayList<>();
        Consumer<Line> onLine = line -> {
            logger.info("Found line: " + line);
            receivedLines.add(line);
        };
        databaseSource.get(noFilters, noFilters, Optional.empty(), Direction.ASC, 10, onLine);

        Wait.forCondition(() -> receivedLines.size() == 10, 3L, ChronoUnit.SECONDS);
    }

    @Test
    public void shouldGetLinesMatchingCtxAndLoggerPrefix() throws Exception {
        dbUtils.insertLine(2, 1, "other." + DEFAULT_LOGGER);
        dbUtils.insertLine(2, 1, DEFAULT_LOGGER);
        dbUtils.insertLine(5, 2, DEFAULT_LOGGER);
        dbUtils.insertLine(5, 2, DEFAULT_LOGGER + ".build-log");

        List<Line> anyLines = new ArrayList<>();
        Consumer<Line> anyLine = (line -> {
            logger.info("Found line: " + line);
            anyLines.add(line);
        });

        Map<String, List<String>> defaultLogMatcher = Collections
                .singletonMap("loggerName", Arrays.asList(DEFAULT_LOGGER));
        databaseSource.get(Collections.emptyMap(), defaultLogMatcher, Optional.empty(), Direction.ASC, 100, anyLine);
        Assertions.assertEquals(12, anyLines.size());

        Map<String, List<String>> matchFilters = new HashMap<>();
        matchFilters.put("processContext", Arrays.asList("2"));
        List<Line> matchingLines = new ArrayList<>();
        Consumer<Line> onLine = (line -> {
            logger.info("Found line: " + line);
            matchingLines.add(line);
        });
        databaseSource.get(matchFilters, defaultLogMatcher, Optional.empty(), Direction.ASC, 100, onLine);
        Assertions.assertEquals(10, matchingLines.size());
    }

    @Test
    public void shouldGetLinesAfter() throws Exception {
        dbUtils.insertLine(10, 1, DEFAULT_LOGGER, Instant.now());

        List<Line> lines = new ArrayList<>();
        Consumer<Line> onLine = (line -> {
            logger.info("Found line: " + line);
            lines.add(line);
        });

        Map<String, List<String>> noFilters = Collections.emptyMap();

        databaseSource.get(noFilters, noFilters, Optional.empty(), Direction.ASC, 5, onLine);
        Assertions.assertEquals(5, lines.size());

        Line lastLine = lines.get(lines.size() - 1);
        databaseSource.get(noFilters, noFilters, Optional.ofNullable(lastLine), Direction.ASC, 100, onLine);
        Assertions.assertEquals(10, lines.size());

        List<Line> sorted = lines.stream()
                .sorted(Comparator.comparingInt(line -> Integer.parseInt(line.getSequence())))
                .collect(Collectors.toList());

        Assertions.assertArrayEquals(lines.toArray(), sorted.toArray());
    }

    @Test
    public void shouldGetLinesAfterDescending() throws Exception {
        dbUtils.insertLine(10, 1, DEFAULT_LOGGER, Instant.now());

        List<Line> lines = new ArrayList<>();
        Consumer<Line> onLine = (line -> {
            logger.info("Found line: " + line);
            lines.add(line);
        });

        Map<String, List<String>> noFilters = Collections.emptyMap();

        databaseSource.get(noFilters, noFilters, Optional.empty(), Direction.DESC, 5, onLine);
        Assertions.assertEquals(5, lines.size());

        Line lastLine = lines.get(lines.size() - 1);
        databaseSource.get(noFilters, noFilters, Optional.ofNullable(lastLine), Direction.DESC, 100, onLine);
        Assertions.assertEquals(10, lines.size());

        List<Line> sorted = lines.stream()
                .sorted(Comparator.comparingInt(line -> Integer.parseInt(((Line) line).getSequence())).reversed())
                .collect(Collectors.toList());

        Assertions.assertArrayEquals(lines.toArray(), sorted.toArray());
    }

}
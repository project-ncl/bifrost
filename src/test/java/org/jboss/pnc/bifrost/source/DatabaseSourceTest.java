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

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.source.db.DatabaseSource;
import org.jboss.pnc.bifrost.source.db.LogLine;
import org.jboss.pnc.bifrost.test.DbUtils;
import org.jboss.pnc.bifrost.test.Wait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
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
public class DatabaseSourceTest {

    private static Logger logger = LoggerFactory.getLogger(DatabaseSourceTest.class);

    private static final String DEFAULT_LOGGER = "org.jboss.pnc._userlog_";

    @Inject
    DbUtils dbUtils;

    @Inject
    DatabaseSource databaseSource;

    @BeforeEach
    @Transactional
    public void init() throws Exception {
        // clean up the DB
        LogLine.deleteAll();
    }

    @Test
    public void shouldReadData() throws Exception {
        dbUtils.insertLines(10, 1, DEFAULT_LOGGER);
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
        dbUtils.insertLines(2, 1, "other." + DEFAULT_LOGGER);
        dbUtils.insertLines(2, 1, DEFAULT_LOGGER);
        dbUtils.insertLines(5, 2, DEFAULT_LOGGER);
        dbUtils.insertLines(5, 2, DEFAULT_LOGGER + ".build-log");

        List<Line> anyLines = new ArrayList<>();
        Consumer<Line> anyLine = (line -> {
            logger.info("Found line: " + line);
            anyLines.add(line);
        });

        // level:INFO is intentionally put as invalid prefixFilter
        Map<String, List<String>> prefixFilters = Map
                .of("loggerName", Arrays.asList(DEFAULT_LOGGER), "level", Arrays.asList("INFO", "ERROR"));

        databaseSource.get(Collections.emptyMap(), prefixFilters, Optional.empty(), Direction.ASC, 100, anyLine);
        Assertions.assertEquals(12, anyLines.size());

        Map<String, List<String>> matchFilters = new HashMap<>();
        matchFilters.put("mdc.processContext", Arrays.asList("2"));
        List<Line> matchingLines = new ArrayList<>();
        Consumer<Line> onLine = (line -> {
            logger.info("Found line: " + line);
            matchingLines.add(line);
        });
        databaseSource.get(matchFilters, prefixFilters, Optional.empty(), Direction.ASC, 100, onLine);
        Assertions.assertEquals(10, matchingLines.size());
    }

    @Test
    public void shouldGetLinesAfter() throws Exception {
        dbUtils.insertLines(10, 1, DEFAULT_LOGGER, OffsetDateTime.now());

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
        dbUtils.insertLines(10, 1, DEFAULT_LOGGER, OffsetDateTime.now());

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

    @Test
    public void shouldGetLinesAfterTimeStamp() throws Exception {
        OffsetDateTime time0 = OffsetDateTime.now();
        /*
         * Round the nano of the timestamp to zero to avoid flaky tests timestamp in java is stored with nano precision,
         * but timestamp in the database is stored with micro precision. Due to rounding errors when comparing the nano
         * precision in Java with the micro precision in the db, from time to time the test will return 3 lines instead
         * of 4.
         *
         * We avoid the rounding errors by just setting the nano part to zero. Good enough for tests!
         */
        time0 = time0.minusNanos(time0.getNano());
        logger.info("Time0: {}.", time0);
        OffsetDateTime time1 = time0.plusMinutes(5);
        dbUtils.insertLines(1, 1, DEFAULT_LOGGER, time0);
        dbUtils.insertLines(1, 1, DEFAULT_LOGGER, time1);
        dbUtils.insertLines(1, 1, DEFAULT_LOGGER, time0.plusMinutes(10));
        dbUtils.insertLines(1, 1, DEFAULT_LOGGER, time0.plusMinutes(15));
        dbUtils.insertLines(1, 1, DEFAULT_LOGGER, time0.plusMinutes(20));

        List<Line> lines = new ArrayList<>();
        Consumer<Line> onLine = (line -> {
            logger.info("Found line: " + line);
            lines.add(line);
        });

        Map<String, List<String>> noFilters = Collections.emptyMap();
        Line searchAfter = Line.newBuilder().timestamp(time1.toString()).build();
        databaseSource.get(noFilters, noFilters, Optional.of(searchAfter), Direction.ASC, 100, onLine);
        for (Line line : lines) {
            logger.info("Line obtained from database: " + line.getTimestamp());
        }
        Assertions.assertEquals(4, lines.size());
    }

}
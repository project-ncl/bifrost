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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.source.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Dependent
public class DatabaseSource implements Source {

    private final Logger logger = LoggerFactory.getLogger(DatabaseSource.class);

    @Inject
    MeterRegistry registry;

    private Counter errCounter;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.of("UTC"));
    private Map<String, Class> logRecordTypes;

    @PostConstruct
    void initMetrics() {
        errCounter = registry.counter(DatabaseSource.class.getName() + ".error.count");
        logRecordTypes = getLogRecordFieldsType();
    }

    @Override
    @Timed
    public void get(
            Map<String, List<String>> matchFilters,
            Map<String, List<String>> prefixFilters,
            Optional<Line> searchAfter,
            Direction direction,
            int fetchSize,
            Consumer<Line> onLine) throws IOException {
        logger.debug(
                "Searching matchFilters: {}, prefixFilters: {}, searchAfter: {}, direction: {}.",
                matchFilters,
                prefixFilters,
                searchAfter,
                direction);

        QueryWithParameters queryWithParameters = getQueryWithParameters(
                matchFilters,
                prefixFilters,
                searchAfter,
                direction);
        Sort sort = Sort.by("timestamp", "sequence", "id").direction(getSortDirection(direction));

        PanacheQuery<PanacheEntityBase> query = LogRecord
                .find(queryWithParameters.query, sort, queryWithParameters.parameters);
        query.range(0, fetchSize + 1);
        List<LogRecord> rows = query.list();

        logger.info("Received {} rows.", rows.size());

        int rowNum = 0;

        /**
         * loop until fetchSize or all the elements are read note that (fetchSize + 1) is used as a limit in the query
         * to check if there are more results
         */
        Iterator<LogRecord> rowsIterator = rows.iterator();
        while (rowsIterator.hasNext() && rowNum < fetchSize) {
            logger.info(">>> {}", rowNum);
            rowNum++;
            LogRecord row = rowsIterator.next();
            boolean last = !rowsIterator.hasNext();
            logger.info("Getting line: ", row);
            Line line = getLine(row, last);
            onLine.accept(line);
        }

        if (rowNum == 0) {
            logger.debug("There are no results.");
            onLine.accept(null);
        }
    }

    private Line getLine(LogRecord row, boolean last) {
        Map<String, String> mdc = new HashMap<>();
        Optional.ofNullable(row.getProcessContext()).ifPresent(v -> mdc.put("processContext", Long.toString(v)));
        Optional.ofNullable(row.getProcessContextVariant())
                .ifPresent(v -> mdc.put("processContextVariant", Long.toString(v)));
        Optional.ofNullable(row.getBuildId()).ifPresent(v -> mdc.put("buildId", Long.toString(v)));
        mdc.put("requestContext", row.getRequestContext());

        return Line.newBuilder()
                .id(Long.toString(row.getId()))
                .timestamp(DATE_TIME_FORMATTER.format(row.getTimestamp()))
                .sequence(Integer.toString(row.getSequence()))
                .logger(row.getLoggerName())
                .message(row.getLogLine())
                .last(last)
                .mdc(mdc)
                .build();
    }

    @Override
    public void close() {
        // noop - using managed datasource
    }

    @Timed
    private QueryWithParameters getQueryWithParameters(
            Map<String, List<String>> matchFilters,
            Map<String, List<String>> prefixFilters,
            Optional<Line> searchAfter,
            Direction direction) {

        Parameters parameters = new Parameters();
        List<String> queryParts = new ArrayList<>();

        matchFilters.forEach((field, values) -> {
            List<String> valueParts = new ArrayList<>();
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String paramName = "m" + field + valueIndex;
                valueParts.add(field + " = :" + paramName);
                parameters.and(paramName, parseType(values.get(valueIndex), field));
            }
            queryParts.add(valueParts.stream().collect(Collectors.joining(" or ")));
        });

        prefixFilters.forEach((field, values) -> {
            List<String> valueParts = new ArrayList<>();
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String paramName = "p" + field + valueIndex;
                valueParts.add(field + " like :" + paramName);
                parameters.and(paramName, values.get(valueIndex) + "%");
            }
            queryParts.add(valueParts.stream().collect(Collectors.joining(" or ")));
        });

        searchAfter.ifPresent(afterLine -> {
            if (Direction.DESC.equals(direction)) {
                queryParts.add("(timestamp, sequence, id) < (:afterTimestamp, :afterSequence ,:afterId)");
            } else {
                queryParts.add("(timestamp, sequence, id) > (:afterTimestamp, :afterSequence ,:afterId)");
            }

            parameters.and("afterTimestamp", afterLine.getTimestamp());
            parameters.and("afterSequence", afterLine.getSequence());
            parameters.and("afterId", afterLine.getId());
        });

        String query = queryParts.stream().collect(Collectors.joining(" and "));
        return new QueryWithParameters(query, parameters);
    }

    private Object parseType(String value, String fieldName) {
        Class type = logRecordTypes.get(fieldName);
        if (type.equals(String.class)) {
            return value;
        } else if (type.equals(int.class) || type.equals(Integer.class)) {
            return Integer.parseInt(value);
        } else if (type.equals(long.class) || type.equals(Long.class)) {
            return Long.parseLong(value);
        } else if (type.equals(Instant.class)) {
            return Instant.parse(value);
        } else if (type.equals(Boolean.class)) {
            return Boolean.getBoolean(value);
        } else {
            throw new RuntimeException("Unknown type: " + type);
        }
    }

    private Map<String, Class> getLogRecordFieldsType() {
        Map<String, Class> types = new HashMap<>();
        Field[] fields = LogRecord.class.getDeclaredFields();
        for (Field field : fields) {
            types.put(field.getName(), field.getType());
        }
        return types;
    }

    private class QueryWithParameters {

        final String query;
        final Parameters parameters;

        public QueryWithParameters(String query, Parameters parameters) {
            this.query = query;
            this.parameters = parameters;
        }
    }

    private Sort.Direction getSortDirection(Direction direction) {
        switch (direction) {
            case ASC:
                return Sort.Direction.Ascending;
            case DESC:
                return Sort.Direction.Descending;
            default:
                errCounter.increment();
                throw new RuntimeException("Unsupported direction: " + direction);
        }
    }
}

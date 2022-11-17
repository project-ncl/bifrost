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
import org.jboss.pnc.common.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
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

    @Inject
    FieldMapping fieldMapping;

    private Counter errCounter;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.of("UTC"));

    @PostConstruct
    void initMetrics() {
        errCounter = registry.counter(DatabaseSource.class.getName() + ".error.count");
    }

    @Override
    @Timed
    @ActivateRequestContext // prevent javax.enterprise.context.ContextNotActiveException
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

        Map<String, List<String>> sanitizedMatchFilters = new HashMap<>(matchFilters);
        Map<String, List<String>> sanitizedPrefixFilters = new HashMap<>(prefixFilters);
        sanitizeFilters(sanitizedMatchFilters, sanitizedPrefixFilters);

        QueryWithParameters queryWithParameters = getQueryWithParameters(
                sanitizedMatchFilters,
                sanitizedPrefixFilters,
                searchAfter,
                direction);
        Sort sort = Sort.by("logLine.eventTimestamp", "logLine.sequence", "logLine.id")
                .direction(getSortDirection(direction));

        String filter;
        if (!Strings.isEmpty(queryWithParameters.query)) {
            filter = " where " + queryWithParameters.query;
        } else {
            filter = "";
        }

        PanacheQuery<PanacheEntityBase> query = LogLine.find(
                "from LogLine logLine left join fetch logLine.logEntry logEntry" + filter,
                sort,
                queryWithParameters.parameters);
        query.range(0, fetchSize + 1);
        List<LogLine> rows = query.list();

        logger.info("Received {} rows.", rows.size());

        int rowNum = 0;

        /**
         * loop until fetchSize or all the elements are read note that (fetchSize + 1) is used as a limit in the query
         * to check if there are more results
         */
        Iterator<LogLine> rowsIterator = rows.iterator();
        while (rowsIterator.hasNext() && rowNum < fetchSize) {
            rowNum++;
            LogLine row = rowsIterator.next();
            boolean last = !rowsIterator.hasNext();
            Line line = getLine(row, last);
            onLine.accept(line);
        }

        if (rowNum == 0) {
            logger.debug("There are no results.");
            onLine.accept(null);
        }
    }

    private void sanitizeFilters(Map<String, List<String>> matchFilters, Map<String, List<String>> prefixFilters) {
        Map<String, List<String>> invalidPrefixFilters = new HashMap<>();

        prefixFilters.forEach((dtoField, values) -> {
            FieldMapping.Field field = fieldMapping.getField(dtoField)
                    .orElseThrow(() -> new InvalidFieldException("The field [" + dtoField + "] is not mapped."));
            if (field.allowExactMatchOnly()) {
                invalidPrefixFilters.put(dtoField, values);
            }
        });
        if (!invalidPrefixFilters.isEmpty()) {
            logger.warn("Found invalid prefix filters, moving them to exact match: {}.", invalidPrefixFilters);
        }

        invalidPrefixFilters.forEach((dtoField, values) -> {
            matchFilters.put(dtoField, values);
            prefixFilters.remove(dtoField);
        });
    }

    private Line getLine(LogLine row, boolean last) {
        Map<String, String> mdc = new HashMap<>();
        Optional.ofNullable(row.getLogEntry().getProcessContext())
                .ifPresent(v -> mdc.put("processContext", Long.toString(v)));
        Optional.ofNullable(row.getLogEntry().getProcessContextVariant())
                .ifPresent(v -> mdc.put("processContextVariant", v));
        Optional.ofNullable(row.getLogEntry().getBuildId()).ifPresent(v -> mdc.put("buildId", Long.toString(v)));
        mdc.put("requestContext", row.getLogEntry().getRequestContext());

        return Line.newBuilder()
                .id(Long.toString(row.getId()))
                .timestamp(DATE_TIME_FORMATTER.format(row.getEventTimestamp()))
                .sequence(Integer.toString(row.getSequence()))
                .logger(row.getLoggerName())
                .message(row.getLine())
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
        List<String> andQueryParts = new ArrayList<>();

        matchFilters.forEach((dtoField, values) -> {
            List<String> valueParts = new ArrayList<>();
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                FieldMapping.Field field = fieldMapping.getField(dtoField)
                        .orElseThrow(() -> new InvalidFieldException("The field [" + dtoField + "] is not mapped."));

                String paramName = "m" + field.name + valueIndex;
                valueParts.add(field.hqlField() + " = :" + paramName);

                String value = values.get(valueIndex);
                parameters.and(paramName, field.valueConverter().convert(value));
            }
            String orParts = valueParts.stream().collect(Collectors.joining(" or "));
            if (!Strings.isEmpty(orParts)) {
                andQueryParts.add("(" + orParts + ")");
            }
        });

        // 'like' queries are string only, no need to convert the value
        prefixFilters.forEach((dtoField, values) -> {
            List<String> valueParts = new ArrayList<>();
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                FieldMapping.Field field = fieldMapping.getField(dtoField)
                        .orElseThrow(() -> new InvalidFieldException("The field [" + dtoField + "] is not mapped."));

                String paramName = "p" + field.name + valueIndex;
                valueParts.add(field.hqlField() + " like :" + paramName);

                String value = values.get(valueIndex);
                parameters.and(paramName, field.valueConverter().convert(value) + "%");
            }
            String orParts = valueParts.stream().collect(Collectors.joining(" or "));
            if (!Strings.isEmpty(orParts)) {
                andQueryParts.add("(" + orParts + ")");
            }
        });

        searchAfter.ifPresent(afterLine -> {
            if (Direction.DESC.equals(direction)) {
                andQueryParts.add(
                        "(logLine.eventTimestamp, logLine.sequence, logLine.id) < (:afterTimestamp, :afterSequence ,:afterId)");
            } else {
                andQueryParts.add(
                        "(logLine.eventTimestamp, logLine.sequence, logLine.id) > (:afterTimestamp, :afterSequence ,:afterId)");
            }

            parameters.and("afterTimestamp", OffsetDateTime.parse(afterLine.getTimestamp()));
            if (afterLine.getSequence() != null) {
                parameters.and("afterSequence", Integer.parseInt(afterLine.getSequence()));
            } else {
                parameters.and("afterSequence", 0);
            }
            if (afterLine.getId() != null) {
                parameters.and("afterId", Long.parseLong(afterLine.getId()));
            } else {
                parameters.and("afterId", 0L);
            }
        });

        String query = andQueryParts.stream().collect(Collectors.joining(" and "));
        return new QueryWithParameters(query, parameters);
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

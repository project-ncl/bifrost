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

import org.jboss.pnc.bifrost.source.db.converter.BooleanConverter;
import org.jboss.pnc.bifrost.source.db.converter.IntegerConverter;
import org.jboss.pnc.bifrost.source.db.converter.LogLevelConverter;
import org.jboss.pnc.bifrost.source.db.converter.LongConverter;
import org.jboss.pnc.bifrost.source.db.converter.OffsetDateTimeConverter;
import org.jboss.pnc.bifrost.source.db.converter.StringConverter;
import org.jboss.pnc.bifrost.source.db.converter.ValueConverter;
import org.jboss.pnc.bifrost.source.db.converter.IdConverter;
import org.jboss.pnc.common.Strings;

import java.util.Map;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class FieldMapping {

    /**
     * Map DTO to DB fields
     */
    private final Map<String, Field> mappings;

    public FieldMapping() {
        var stringConverter = new StringConverter();
        var booleanConverter = new BooleanConverter();
        var integerConverter = new IntegerConverter();
        var longConverter = new LongConverter();
        var offsetDateTimeConverter = new OffsetDateTimeConverter();
        var idConverter = new IdConverter();
        var logLevelConverter = new LogLevelConverter();

        // .keyword is used for backward compatibility
        this.mappings = Map.ofEntries(
                Map.entry("id", Field.from(LogLine.class, "id", longConverter, true)),
                Map.entry("timestamp", Field.from(LogLine.class, "eventTimestamp", offsetDateTimeConverter, true)),
                Map.entry("sequence", Field.from(LogLine.class, "sequence", longConverter, true)),
                Map.entry("loggerName", Field.from(LogLine.class, "loggerName", stringConverter, false)),
                Map.entry("loggerName.keyword", Field.from(LogLine.class, "loggerName", stringConverter, false)),
                Map.entry("level", Field.from(LogLine.class, "level", logLevelConverter, true)),
                Map.entry("level.keyword", Field.from(LogLine.class, "level", logLevelConverter, true)),
                Map.entry("mdc.processContext", Field.from(LogEntry.class, "processContext", idConverter, true)),
                Map.entry(
                        "mdc.processContext.keyword",
                        Field.from(LogEntry.class, "processContext", idConverter, true)),
                Map.entry(
                        "mdc.processContextVariant",
                        Field.from(LogEntry.class, "processContextVariant", stringConverter, true)),
                Map.entry("mdc.requestContext", Field.from(LogEntry.class, "requestContext", stringConverter, true)),
                Map.entry("mdc.buildId", Field.from(LogEntry.class, "buildId", idConverter, true)),
                Map.entry("mdc.buildId.keyword", Field.from(LogEntry.class, "buildId", idConverter, true)),
                Map.entry("mdc.tmp", Field.from(LogEntry.class, "temporary", booleanConverter, true)));
    }

    public Optional<Field> getField(String dtoField) {
        return Optional.ofNullable(mappings.get(dtoField));
    }

    public static class Field<T> {
        final Class clazz;
        final String name;
        final ValueConverter<T> valueConverter;
        private boolean allowExactMatchOnly;

        public Field(Class clazz, String name, ValueConverter valueConverter, boolean allowExactMatchOnly) {
            this.clazz = clazz;
            this.name = name;
            this.valueConverter = valueConverter;
            this.allowExactMatchOnly = allowExactMatchOnly;
        }

        public static <T> Field<T> from(
                Class clazz,
                String fieldName,
                ValueConverter<T> valueConverter,
                boolean allowExactMatchOnly) {
            return new Field(clazz, fieldName, valueConverter, allowExactMatchOnly);
        }

        public ValueConverter valueConverter() {
            return valueConverter;
        }

        public String hqlField() {
            return Strings.fistCharToLower(clazz.getSimpleName()) + "." + name;
        }

        public boolean allowExactMatchOnly() {
            return allowExactMatchOnly;
        }
    }
}

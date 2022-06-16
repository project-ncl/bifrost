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
import org.jboss.pnc.bifrost.source.db.converter.idConverter;
import org.jboss.pnc.bifrost.source.db.converter.StringConverter;
import org.jboss.pnc.bifrost.source.db.converter.ValueConverter;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;

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
        var idConverter = new idConverter();
        var logLevelConverter = new LogLevelConverter();

        // .keyword is used for backward compatibility
        this.mappings = Map.ofEntries(
                Map.entry("id", Field.from(LogLine.class, "id", longConverter)),
                Map.entry("timestamp", Field.from(LogLine.class, "eventTimestamp", offsetDateTimeConverter)),
                Map.entry("sequence", Field.from(LogLine.class, "sequence", integerConverter)),
                Map.entry("loggerName", Field.from(LogLine.class, "loggerName", stringConverter)),
                Map.entry("loggerName.keyword", Field.from(LogLine.class, "loggerName", stringConverter)),
                Map.entry("level", Field.from(LogLine.class, "level", logLevelConverter)),
                Map.entry("level.keyword", Field.from(LogLine.class, "level", logLevelConverter)),
                Map.entry("mdc.processContext", Field.from(LogEntry.class, "processContext", idConverter)),
                Map.entry("mdc.processContext.keyword", Field.from(LogEntry.class, "processContext", idConverter)),
                Map.entry(
                        "mdc.processContextVariant",
                        Field.from(LogEntry.class, "processContextVariant", stringConverter)),
                Map.entry("mdc.requestContext", Field.from(LogEntry.class, "requestContext", stringConverter)),
                Map.entry("mdc.buildId", Field.from(LogEntry.class, "buildId", idConverter)),
                Map.entry("mdc.buildId.keyword", Field.from(LogEntry.class, "buildId", idConverter)),
                Map.entry("mdc.tmp", Field.from(LogEntry.class, "temporary", booleanConverter)));
    }

    public Optional<Field> getField(String dtoField) {
        return Optional.ofNullable(mappings.get(dtoField));
    }

    public static class Field<T> {
        final Class clazz;
        final String name;
        final ValueConverter<T> valueConverter;

        public Field(Class clazz, String name, ValueConverter valueConverter) {
            this.clazz = clazz;
            this.name = name;
            this.valueConverter = valueConverter;
        }

        public static <T> Field<T> from(Class clazz, String fieldName, ValueConverter<T> valueConverter) {
            return new Field(clazz, fieldName, valueConverter);
        }

        public ValueConverter valueConverter() {
            return valueConverter;
        }
    }
}

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
        // .keyword is used for backward compatibility
        this.mappings = Map.ofEntries(
                Map.entry("id", Field.from(LogLine.class, "id")),
                Map.entry("timestamp", Field.from(LogLine.class, "eventTimestamp")),
                Map.entry("sequence", Field.from(LogLine.class, "sequence")),
                Map.entry("loggerName", Field.from(LogLine.class, "loggerName")),
                Map.entry("loggerName.keyword", Field.from(LogLine.class, "loggerName")),
                Map.entry("level", Field.from(LogLine.class, "level")),
                Map.entry("level.keyword", Field.from(LogLine.class, "level")),
                Map.entry("mdc.processContext", Field.from(LogEntry.class, "processContext")),
                Map.entry("mdc.processContext.keyword", Field.from(LogEntry.class, "processContext")),
                Map.entry("mdc.processContextVariant", Field.from(LogEntry.class, "processContextVariant")),
                Map.entry("mdc.requestContext", Field.from(LogEntry.class, "requestContext")),
                Map.entry("mdc.buildId", Field.from(LogEntry.class, "buildId")),
                Map.entry("mdc.buildId.keyword", Field.from(LogEntry.class, "logEntry.buildId")));
    }

    public Optional<Field> getDbField(String dtoField) {
        return Optional.ofNullable(mappings.get(dtoField));
    }

    public static class Field {
        Class clazz;
        String name;

        Field(Class clazz, String name) {
            this.clazz = clazz;
            this.name = name;
        }

        public static Field from(Class clazz, String fieldName) {
            return new Field(clazz, fieldName);
        }
    }
}

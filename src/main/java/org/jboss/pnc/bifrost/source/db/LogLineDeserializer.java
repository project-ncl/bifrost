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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.jboss.logging.Logger;
import org.jboss.pnc.bifrost.source.db.converter.ValueConverter;
import org.jboss.pnc.bifrost.source.db.converter.idConverter;
import org.jboss.pnc.common.Json;
import org.jboss.pnc.common.Strings;
import org.jboss.pnc.common.concurrent.Sequence;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Use to deserialize json messages from a Kafka stream.
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class LogLineDeserializer extends StdDeserializer<LogLine> {

    private final Logger logger = Logger.getLogger(LogLineDeserializer.class);

    private final ValueConverter<Long> processContextConverter = new idConverter();

    public LogLineDeserializer() {
        super(LogLine.class);
    }

    protected LogLineDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public LogLine deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException {

        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        int sequence = Json.<Integer> getNumber(node, "/sequence").orElse(0);

        String time = Json.getText(node, "/@timestamp").or(() -> Json.getText(node, "/timestamp")).orElseGet(() -> {
            logger.warn("Missing timestamp in input message, setting it now.");
            return Instant.now().toString();
        });

        Instant timestamp = Instant.parse(time);
        String level = Json.getText(node, "/level").orElse(null);
        String loggerName = Json.getText(node, "/loggerName").orElse(null);
        String processContext = Json.getText(node, "/mdc/processContext").orElse(null);
        String processContextVariant = Json.getText(node, "/mdc/processContextVariant").orElse(null);
        String requestContext = Json.getText(node, "/mdc/requestContext").orElse(null);
        Boolean temp = Boolean.parseBoolean(Json.getText(node, "/mdc/tmp").orElse("false"));
        Long buildId = Json.<Long> getNumber(node, "/mdc/buildId").orElse(null);

        String message = Json.getText(node, "/message").orElse(null);
        String logLine;
        if (!Strings.isEmpty(message)) {
            logLine = message;
        } else {
            Optional<String> stackTrace = Json.getText(node, "/stackTrace");
            if (stackTrace.isPresent()) {
                logLine = stackTrace.get().lines().findFirst().orElse("");
            } else {
                logLine = "";
            }
        }

        LogEntry logEntry = new LogEntry(
                Sequence.nextId(),
                processContextConverter.convert(processContext),
                processContextVariant,
                requestContext,
                temp,
                buildId);
        return new LogLine(
                Sequence.nextId(),
                logEntry,
                OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC),
                sequence,
                LogLevel.valueOf(level.toUpperCase()),
                loggerName,
                logLine);
    }

}

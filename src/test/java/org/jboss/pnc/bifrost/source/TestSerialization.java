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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.jboss.logging.Logger;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.bifrost.common.Json;
import org.jboss.pnc.bifrost.endpoint.websocket.Result;
import org.jboss.pnc.bifrost.endpoint.websocket.SubscribeResultDto;
import org.jboss.pnc.bifrost.mock.LineProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TestSerialization {

    private final Logger logger = Logger.getLogger(TestSerialization.class);

    @Test
    public void shouldSerializeAndDeserializeLine() throws JsonProcessingException {
        Line line1 = LineProducer.getLine(1, true, "adst");

        String jsonLine = Json.mapper().writeValueAsString(line1);
        logger.info(jsonLine);

        Line fromJson = Json.mapper().readValue(jsonLine, Line.class);
        Assertions.assertEquals(line1.getId(), fromJson.getId());
        Assertions.assertEquals(line1.getTimestamp(), fromJson.getTimestamp());
    }

    @Test
    public void shouldSerializeAndDeserializeLineList() throws JsonProcessingException {
        List<Line> lines = LineProducer.getLines(3, "abc123");

        String jsonLines = Json.mapper().writeValueAsString(lines);
        logger.info("Serialized: " + jsonLines);

        List<Line> deserializedLines = Json.mapper().readValue(jsonLines, new TypeReference<List<Line>>() {
        });
        Assertions.assertEquals(3, deserializedLines.size());
        Assertions.assertEquals("abc123", deserializedLines.get(0).getMdc().get("processContext"));
    }

    @Test
    public void shouldSerializeAndDeserializeSubscriptionResult() throws JsonProcessingException {
        String topic = "myTopic";
        Result result = new SubscribeResultDto(topic);

        String json = Json.mapper().writeValueAsString(result);
        logger.info(json);
        System.out.println(json);

        SubscribeResultDto fromJson = Json.mapper().readValue(json, SubscribeResultDto.class);
        Assertions.assertEquals(topic, fromJson.getValue());
    }
}

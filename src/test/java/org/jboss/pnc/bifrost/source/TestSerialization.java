package org.jboss.pnc.bifrost.source;

import io.quarkus.arc.impl.ParameterizedTypeImpl;
import org.jboss.logging.Logger;
import org.jboss.pnc.bifrost.endpoint.websocket.Result;
import org.jboss.pnc.bifrost.endpoint.websocket.SubscribeResultDto;
import org.jboss.pnc.bifrost.mock.LineProducer;
import org.jboss.pnc.bifrost.source.dto.Line;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TestSerialization {

    private final Logger logger = Logger.getLogger(TestSerialization.class);

    @Test
    public void shouldSerializeAndDeserializeLine() {
        JsonbConfig config = new JsonbConfig().withFormatting(true);
        Jsonb jsonb = JsonbBuilder.create(config);

        Line line1 = LineProducer.getLine(1, true, "adst");

        String jsonLine = jsonb.toJson(line1);
        logger.info(jsonLine);

        Line fromJson = jsonb.fromJson(jsonLine, Line.class);
        Assertions.assertEquals(line1.getId(), fromJson.getId());
        Assertions.assertEquals(line1.getTimestamp(), fromJson.getTimestamp());
    }

    @Test
    public void shouldSerializeAndDeserializeLineList() {
        JsonbConfig config = new JsonbConfig().withFormatting(true);
        Jsonb jsonb = JsonbBuilder.create(config);

        List<Line> lines = LineProducer.getLines(3, "abc123");

        String jsonLines = jsonb.toJson(lines);
        logger.info("Serialized: " + jsonLines);

        List<Line> deserializedLines = jsonb.fromJson(jsonLines, new ParameterizedTypeImpl(List.class, Line.class));
        Assertions.assertEquals(3, deserializedLines.size());
        Assertions.assertEquals("abc123", deserializedLines.get(0).getMdc().get("processContext"));
    }

    @Test
    public void shouldSerializeAndDeserializeSubscriptionResult() {
        JsonbConfig config = new JsonbConfig().withFormatting(true);
        Jsonb jsonb = JsonbBuilder.create(config);

        String topic = "myTopic";
        Result result = new SubscribeResultDto(topic);

        String json = jsonb.toJson(result);
        logger.info(json);
        System.out.println(json);

        SubscribeResultDto fromJson = jsonb.fromJson(json, SubscribeResultDto.class);
        Assertions.assertEquals(topic, fromJson.getValue());
    }
}

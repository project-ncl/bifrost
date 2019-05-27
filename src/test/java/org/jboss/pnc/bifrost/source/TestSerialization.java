package org.jboss.pnc.bifrost.source;

import io.quarkus.arc.ParameterizedTypeImpl;
import org.jboss.logging.Logger;
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
        Assertions.assertEquals("abc123", deserializedLines.get(0).getCtx());
    }

    //
//    @Test
//    public void shouldSerializeAndDeserializeLine() throws IOException {
//        ObjectMapper objectMapper = new ObjectMapper();
//
//        Line line1 = new Line(
//                "1",
//                "1548633600000",
//                this.getClass().getName(),
//                "line1",
//                false,
//                "dsfdo",
//                false,
//                "111111"
//        );
//
//        String asString = objectMapper.writeValueAsString(line1);
//
//        System.out.println(asString);
//
//        Line line = objectMapper.readValue(asString, Line.class);
//
//        byte[] byteLine = objectMapper.writeValueAsBytes(line1);
//        System.out.println("Byteline: " + new String(byteLine));
//
//    }


    /*
    @Test
    public void shouldSerializeOptional() throws IOException {
        ObjectMapper mapper = ObjectMapperProvider.get();

        Optional<String> original = Optional.of("chameleon");

        String string = mapper.writeValueAsString(original);

        System.out.println(string);

        Optional<String> deserialized = mapper.readValue(string, Optional.class);
        Assertions.assertEquals(original, deserialized);
    }

    @Test
    public void shouldSerializeOptional2() throws IOException {
        ObjectMapper mapper = ObjectMapperProvider.get();

        Wrapper original = new Wrapper(Optional.of("chameleon"));

        String string = mapper.writeValueAsString(original);
        System.out.println(string);

        Wrapper deserialized = mapper.readValue(string, Wrapper.class);
        Assertions.assertEquals(original, deserialized);
    }

    public static class Wrapper {
        Optional<String> animal;

        public Wrapper() {
        }

        public Wrapper(Optional<String> animal) {
            this.animal = animal;
        }

        public Optional<String> getAnimal() {
            return animal;
        }

        public void setAnimal(Optional<String> animal) {
            this.animal = animal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Wrapper))
                return false;
            Wrapper wrapper = (Wrapper) o;
            return Objects.equals(animal, wrapper.animal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(animal);
        }
    }
*/

}

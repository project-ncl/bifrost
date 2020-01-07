package org.jboss.pnc.bifrost.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class StringTest {

    @Test
    public void shouldDeserializeQuery() {
        String query = "key1:value1|value2,key2:value22";
        Map<String, List<String>> map = Strings.toMap(query);

        Assertions.assertEquals(map.get("key1").size(), 2);
        Assertions.assertLinesMatch(map.get("key1"), Arrays.asList(new String[]{"value1", "value2"}));

        Assertions.assertLinesMatch(map.get("key2"), Arrays.asList(new String[]{"value22"}));

    }
}

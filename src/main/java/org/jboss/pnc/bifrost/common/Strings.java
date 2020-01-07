package org.jboss.pnc.bifrost.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Strings {

    /**
     * Converts string with key:value1|value2,key2:value22 to a map where key is an entry key and values are a list of items.
     */
    public static Map<String, List<String>> toMap(String string) {
        if (string == null || string.equals("")) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> map = new HashMap<>();
        try {
            String[] pairs = string.split(",");

            for (String pair : pairs) {
                String[] keyValues = pair.split(":");
                String valuesString = keyValues[1];
                String[] values = valuesString.split("\\|");
                map.put(keyValues[0], Arrays.asList(values));
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Invalid key:value string: [" + string + "]", e);
        }
        return map;
    }
}

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
        Assertions.assertLinesMatch(map.get("key1"), Arrays.asList(new String[] { "value1", "value2" }));

        Assertions.assertLinesMatch(map.get("key2"), Arrays.asList(new String[] { "value22" }));

    }
}

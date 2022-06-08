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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.common.Produced;
import org.jboss.pnc.bifrost.test.DbUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
public class SourceTest {

    private final Logger logger = LoggerFactory.getLogger(SourceTest.class);

    @Produced
    @Inject
    Source source;

    @Inject
    DbUtils dbUtils;

    private final Map<String, List<String>> noFilters = Collections.emptyMap();

    @Test
    public void shouldReadFromDBSource() throws Exception {
        dbUtils.insertLines(10, 1L, SourceTest.class.toString());
        List<Line> lines = new ArrayList<>();
        Consumer<Line> onLine = (line) -> {
            logger.debug("Received line: {}.", line);
            lines.add(line);
        };
        source.get(noFilters, noFilters, Optional.empty(), Direction.ASC, 10, onLine);
        Assertions.assertEquals(10, lines.size());
    }

}

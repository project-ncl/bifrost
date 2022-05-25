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

import io.quarkus.arc.All;
import io.quarkus.arc.InstanceHandle;
import org.jboss.pnc.bifrost.Config;
import org.jboss.pnc.bifrost.common.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class SourceProducer {

    private final Logger logger = LoggerFactory.getLogger(SourceProducer.class);

    @Inject
    @All
    List<InstanceHandle<Source>> sources;

    @Inject
    Config config;

    @Produced
    @ApplicationScoped
    Source produceSource() {
        String sourceClass = config.getSourceClass();
        logger.debug("Available sources: {}", sources);
        return sources.stream()
                .filter(
                        s -> s.getBean()
                                .getTypes()
                                .stream()
                                .filter(t -> t.getTypeName().equals(sourceClass))
                                .findAny()
                                .isPresent())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find source for class name: " + sourceClass))
                .get();
    }
}

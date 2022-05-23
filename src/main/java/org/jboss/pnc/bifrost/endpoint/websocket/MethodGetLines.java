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
package org.jboss.pnc.bifrost.endpoint.websocket;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jboss.logging.Logger;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.bifrost.common.scheduler.Subscriptions;
import org.jboss.pnc.bifrost.endpoint.provider.DataProvider;

import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Dependent
public class MethodGetLines extends MethodBase implements Method<GetLinesDto> {

    private static final String className = MethodGetLines.class.getName();

    private Logger logger = Logger.getLogger(MethodGetLines.class);

    @Inject
    DataProvider dataProvider;

    @Inject
    Subscriptions subscriptions;

    @Inject
    MeterRegistry registry;

    private Counter errCounter;

    @PostConstruct
    void initMetrics() {
        errCounter = registry.counter(className + ".error.count");
    }

    @Override
    public String getName() {
        return "GET-LINES";
    }

    @Override
    public Class<GetLinesDto> getParameterType() {
        return GetLinesDto.class;
    }

    @Timed
    @Override
    public Result apply(GetLinesDto in, Consumer<Line> responseConsumer) {
        Consumer<Line> onLine = (line) -> {
            responseConsumer.accept(line);
        };

        // async to complete the request
        subscriptions.submit(() -> {
            try {
                dataProvider.get(
                        in.getMatchFilters(),
                        in.getPrefixFilters(),
                        Optional.ofNullable(in.getAfterLine()),
                        in.getDirection(),
                        Optional.ofNullable(in.getMaxLines()),
                        onLine);
            } catch (Exception e) {
                errCounter.increment();
                logger.error("Unable to get data from Elasticsearch.", e);
            }
        });
        return new OkResult();
    }
}

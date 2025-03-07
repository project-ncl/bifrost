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
package org.jboss.pnc.bifrost.endpoint.provider;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.test.Mock;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.mock.LineProducer;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Mock
@ApplicationScoped
public class DataProviderMock extends DataProvider {

    Deque<Line> lines = new LinkedList<>();
    Optional<IOException> throwOnCall = Optional.empty();

    public DataProviderMock() {
        super();
    }

    @Override
    @WithSpan()
    public void get(
            @SpanAttribute(value = "matchFilters") String matchFilters,
            @SpanAttribute(value = "prefixFilters") String prefixFilters,
            @SpanAttribute(value = "afterLine") Optional<Line> afterLine,
            @SpanAttribute(value = "direction") Direction direction,
            @SpanAttribute(value = "maxLines") Optional<Integer> maxLines,
            @SpanAttribute(value = "batchSize") Optional<Integer> batchSize,
            @SpanAttribute(value = "onLine") Consumer<Line> onLine) throws IOException {
        if (throwOnCall.isPresent()) {
            throw throwOnCall.get();
        } else {
            LineProducer.getLines(5, "abc123").forEach(line -> onLine.accept(line));
        }
    }

    @Override
    @WithSpan()
    protected void readFromSource(
            @SpanAttribute(value = "matchFilters") String matchFilters,
            @SpanAttribute(value = "prefixFilters") String prefixFilters,
            @SpanAttribute(value = "fetchSize") int fetchSize,
            @SpanAttribute(value = "lastResult") Optional<Line> lastResult,
            @SpanAttribute(value = "onLine") Consumer<Line> onLine) throws IOException {

        for (int i = 0; i < fetchSize; i++) {
            if (lines.isEmpty()) {
                break;
            }
            Line line = lines.pop();
            onLine.accept(line);
        }
    }

    public void addLine(Line line) {
        lines.add(line);
    }

    public void addAllLines(List<Line> lines) {
        for (Line line : lines) {
            addLine(line);
        }
    }

    public void setThrowOnCall(IOException e) {
        this.throwOnCall = Optional.of(e);
    }

    public void removeThrowOnCall() {
        this.throwOnCall = Optional.empty();
    }
}
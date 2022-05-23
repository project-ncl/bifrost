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
package org.jboss.pnc.bifrost.common.scheduler;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@QuarkusTest
public class SubscriptionsTest {

    private final Logger logger = Logger.getLogger(SubscriptionsTest.class);

    @Inject
    BackOffRunnableConfig backOffRunnableConfig;

    @Inject
    Subscriptions subscriptions;

    @Test
    public void shouldSubcribeTaskAndRunIt() throws TimeoutException, InterruptedException {
        BlockingQueue<String> resultsQueue = new ArrayBlockingQueue<>(100);
        Consumer<String> onResult = line -> {
            resultsQueue.add(line);
        };

        Subscription subscription = new Subscription("1", "A", () -> {});

        Consumer<Subscriptions.TaskParameters<String>> task = (parameters) -> {
            for (int i = 0; i < 5; i++) {
                parameters.getResultConsumer().accept("Result " + i + ". Last was: " + parameters.getLastResult());
            }
        };
        subscriptions.subscribe(subscription, task, Optional.empty(), onResult, backOffRunnableConfig);

        List<String> results = new ArrayList<>();
        while (results.size() < 15) {
            String result = resultsQueue.poll(5, TimeUnit.SECONDS);
            results.add(result);
        }
        results.forEach(r -> logger.info(r));
        Assertions.assertTrue(results.get(0).startsWith("Result 0"));

        subscriptions.unsubscribeAll();
    }
}

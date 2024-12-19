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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.context.Context;
import jakarta.annotation.PostConstruct;
import org.apache.lucene.util.NamedThreadFactory;
import org.jboss.pnc.bifrost.Config;
import org.jboss.pnc.bifrost.common.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class Subscriptions {

    private static final String className = Subscriptions.class.getName();

    private Logger logger = LoggerFactory.getLogger(Subscriptions.class);

    private Map<Subscription, ScheduledFuture> subscriptions;

    private ScheduledExecutorService executor;

    @Inject
    MeterRegistry registry;

    private Gauge subscriptionsMapSize;

    @PostConstruct
    void initMetrics() {
        subscriptionsMapSize = Gauge
                .builder(className + ".subscriptions.map.size", this, Subscriptions::getSubscriptionsMapSize)
                .description("current subscriptions map size")
                .register(registry);
    }

    private int getSubscriptionsMapSize() {
        return subscriptions.size();
    }

    /**
     * CDI workaround
     */
    @Deprecated
    public Subscriptions() {
    }

    @Inject
    public Subscriptions(Config config) {
        subscriptions = new ConcurrentHashMap<>();
        executor = Context.current()
                .wrap(
                        Executors.newScheduledThreadPool(
                                config.getSourcePollThreads(),
                                new NamedThreadFactory("subscriptions")));
    }

    public void submit(Runnable task) {
        executor.submit(task);
    }

    public <T> void run(Consumer<TaskParameters<T>> task, Optional<T> initialLastResult, Consumer<T> onResult) {
        task.accept(new TaskParameters(initialLastResult.get(), onResult));
    }

    public <T> void submit(Consumer<TaskParameters<T>> task, Optional<T> initialLastResult, Consumer<T> onResult) {
        executor.submit(() -> task.accept(new TaskParameters(initialLastResult.get(), onResult)));
    }

    @Timed
    public <T> void subscribe(
            Subscription subscription,
            Consumer<TaskParameters<T>> task,
            Optional<T> initialLastResult,
            Consumer<T> onResult,
            BackOffRunnableConfig backOffRunnableConfig,
            Optional<Integer> batchDelay) {

        BackOffRunnable backOffRunnable = new BackOffRunnable(backOffRunnableConfig);

        Reference<T> lastResult = new Reference<>(initialLastResult.orElse(null));
        Runnable internalTask = () -> {
            Consumer<T> onResultInternal = result -> {
                if (result != null) { // null indicates end of message stream
                    lastResult.set(result);
                    backOffRunnable.receivedResult();
                }
                onResult.accept(result);
            };

            task.accept(new TaskParameters(lastResult.get(), onResultInternal));

        };

        backOffRunnable.setRunnable(internalTask);
        backOffRunnable.setCancelHook(() -> unsubscribe(subscription, UnsubscribeReason.NO_DATA_FROM_SOURCE));
        ScheduledFuture<?> scheduledFuture = executor.scheduleAtFixedRate(
                backOffRunnable,
                0,
                batchDelay.map(Long::valueOf).orElse(backOffRunnableConfig.getPollIntervalMillis()),
                TimeUnit.MILLISECONDS);
        subscriptions.put(subscription, scheduledFuture);
    }

    public void unsubscribe(Subscription subscription, UnsubscribeReason reason) {
        logger.info("Unsubscribing: " + subscription + " Reason:" + reason);
        ScheduledFuture scheduledFuture = subscriptions.remove(subscription);
        if (UnsubscribeReason.NO_DATA_FROM_SOURCE.equals(reason)) {
            subscription.runOnUnsubscribe();
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            logger.info("Cancelled: " + subscription);
        }
    }

    public void unsubscribe(Subscription subscription) {
        unsubscribe(subscription, UnsubscribeReason.OTHER);
    }

    public void unsubscribeAll() {
        subscriptions.keySet().stream().forEach(s -> unsubscribe(s));
    }

    public Set<Subscription> getAll() {
        return Collections.unmodifiableSet(subscriptions.keySet());
    }

    public enum UnsubscribeReason {
        NO_DATA_FROM_SOURCE, OTHER;
    }

    public static class TaskParameters<T> {

        private T lastResult;

        private Consumer<T> resultConsumer;

        public TaskParameters(T lastResult, Consumer<T> resultConsumer) {
            this.lastResult = lastResult;
            this.resultConsumer = resultConsumer;
        }

        public T getLastResult() {
            return lastResult;
        }

        public Consumer<T> getResultConsumer() {
            return resultConsumer;
        }
    }
}

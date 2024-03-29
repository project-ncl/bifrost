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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
class TimeoutExecutorTest {

    private Logger logger = LoggerFactory.getLogger(TimeoutExecutorTest.class);

    @Test
    void shouldRunTaskAfterTimeout() throws InterruptedException {
        TimeoutExecutor timeoutExecutor = new TimeoutExecutor(new ScheduledThreadPoolExecutor(2));

        AtomicInteger run = new AtomicInteger();
        Runnable runnable = () -> {
            int i = run.incrementAndGet();
        };
        TimeoutExecutor.Task task = timeoutExecutor.submit(runnable, 100, TimeUnit.MILLISECONDS);
        for (int i = 0; i < 10; i++) {
            TimeUnit.MILLISECONDS.sleep(50);
            task.update();
            Assertions.assertEquals(0, run.get());
        }
        TimeUnit.MILLISECONDS.sleep(110);
        Assertions.assertEquals(1, run.get());
        TimeUnit.MILLISECONDS.sleep(110);
        Assertions.assertEquals(2, run.get());
        task.cancel();
        TimeUnit.MILLISECONDS.sleep(110);
        Assertions.assertEquals(2, run.get());
    }
}
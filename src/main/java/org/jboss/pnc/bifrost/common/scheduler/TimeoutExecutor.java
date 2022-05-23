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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TimeoutExecutor {

    private ScheduledExecutorService executorService;

    public TimeoutExecutor(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    public Task submit(Runnable task, long runTimeout, TimeUnit timeUnit) {
        ScheduledFuture<?> future = schedule(task, runTimeout, timeUnit);
        return new Task(future, task, runTimeout, timeUnit);
    }

    private ScheduledFuture<?> schedule(Runnable task, long runTimeout, TimeUnit timeUnit) {
        return executorService.scheduleAtFixedRate(task, runTimeout, runTimeout, timeUnit);
    }

    public class Task {
        private final Runnable task;
        private final long runTimeout;
        private final TimeUnit timeUnit;
        private ScheduledFuture<?> future;

        public Task(ScheduledFuture<?> future, Runnable task, long runTimeout, TimeUnit timeUnit) {
            this.future = future;
            this.task = task;
            this.runTimeout = runTimeout;
            this.timeUnit = timeUnit;
        }

        synchronized public void update() {
            boolean canceled = future.cancel(true);
            if (canceled) {
                future = schedule(task, runTimeout, timeUnit);
            }
        }

        synchronized public void cancel() {
            future.cancel(false);
        }
    }

}

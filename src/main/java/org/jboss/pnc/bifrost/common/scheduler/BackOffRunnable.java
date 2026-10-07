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

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.context.Context;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class BackOffRunnable implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(BackOffRunnable.class);

    private final BackOffRunnableConfig config;

    private Long lastResult = 0L;
    private Long backOffNextCycles = 0L;

    private Runnable runnable;

    private Optional<Runnable> cancelHook = Optional.empty();

    public BackOffRunnable(BackOffRunnableConfig backOffRunnableConfig) {
        this.config = backOffRunnableConfig;
    }

    public void setRunnable(Runnable runnable) {
        this.runnable = Context.current().wrap(runnable);
    }

    @Override
    public void run() {
        try {
            if (lastResult == 0L) {
                lastResult = System.currentTimeMillis() - config.getDelayMillis();
            }
            validateTimeout();
            if (backOffNextCycles > 0L) {
                backOffNextCycles--;
                logger.debug("Cycle skipped.");
                return;
            }
            Long backOff = (System.currentTimeMillis() - lastResult) / config.getDelayMillis();

            backOffNextCycles = Long.min(backOff - 1, config.getMaxBackOffCycles());
            logger.info("Running task ...");
            runnable.run();
        } catch (Exception e) {
            logger.error("Error executing task.", e);
            throw e;
        }
    }

    private void validateTimeout() {
        if (System.currentTimeMillis() - lastResult > config.getTimeOutMillis()) {
            cancelHook.ifPresent(Runnable::run);
        }
    }

    public void receivedResult() {
        this.lastResult = System.currentTimeMillis();
    }

    public void setCancelHook(Runnable cancelHook) {
        this.cancelHook = Optional.ofNullable(Context.current().wrap(cancelHook));
    }
}

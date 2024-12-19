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
package org.jboss.pnc.bifrost;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.jboss.pnc.bifrost.kafkaconsumer.Configuration;
import org.jboss.pnc.bifrost.kafkaconsumer.StoredCounter;
import org.jboss.pnc.common.Strings;
import org.jboss.pnc.common.concurrent.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Lifecycle {
    private final Logger logger = LoggerFactory.getLogger(Lifecycle.class);

    @Inject
    Configuration configuration;

    @Inject
    StoredCounter storedCounter;

    public void start(@Observes StartupEvent event) throws IOException {
        logger.info("Starting application ...");
        if (!Strings.isEmpty(configuration.hostname())) {
            int nodeId = podOrdinalNumber(configuration.hostname()) + configuration.clusterSequence();
            logger.info("Initializing sequence generator with nodeId: {}.", nodeId);
            Sequence.setNodeId(nodeId);
        }
        storedCounter.addIncrementListener(cont -> {
            if (cont % configuration.logEveryNMessages() == 0) {
                logger.info("Stored {} messages.", configuration.logEveryNMessages());
            }
        });

    }

    public void stop(@Observes ShutdownEvent event) {
        logger.info("Stopping application ...");
    }

    /**
     * Extract pod ordinal number managed by StatefulSet out of hostname.
     */
    public static int podOrdinalNumber(String hostname) {
        return Integer.parseInt(hostname.substring(hostname.lastIndexOf("-") + 1));
    }
}

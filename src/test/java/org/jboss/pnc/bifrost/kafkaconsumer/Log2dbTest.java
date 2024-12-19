/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.bifrost.kafkaconsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.jboss.pnc.bifrost.em.HibernateMetric;
import org.jboss.pnc.bifrost.em.HibernateStatsUtils;
import org.jboss.pnc.bifrost.source.db.LogLine;
import org.jboss.pnc.bifrost.test.DbUtils;
import org.jboss.pnc.common.concurrent.Sequence;
import org.jboss.pnc.common.pnc.LongBase32IdConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.Semaphore;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
public class Log2dbTest {

    private final Logger logger = LoggerFactory.getLogger(Log2dbTest.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    @Channel("logs-in")
    Emitter<String> emitter;

    @Inject
    StoredCounter storedCounter;

    @Inject
    DbUtils dbUtils;

    @Inject
    EntityManager entityManager;

    @Test
    @Timeout(10)
    public void logsShouldBeStored() throws InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        storedCounter.addIncrementListener(count -> {
            if (count == 10) {
                semaphore.release();
            }
        });
        emitMessages(10, "org.apache"); // excluded by config
        emitMessages(10, "org.jboss.pnc");
        semaphore.acquire();

        // give some time for the commit to finish
        Thread.sleep(1000);

        List<LogLine> stored = LogLine.listAll();
        Assertions.assertEquals(10, stored.size());
        stored.forEach(r -> {
            if (r.getLoggerName().startsWith("org.apache")) {
                Assertions.fail("LoggerName org.apache should not be stored.");
            }
        });

        SessionFactory sessionFactory = ((Session) entityManager.getDelegate()).getSessionFactory();
        SortedMap<String, Map<String, HibernateMetric>> cacheEntitiesStats = HibernateStatsUtils
                .getSecondLevelCacheEntitiesStats(sessionFactory.getStatistics());
        logger.info("Cache stat: ", cacheEntitiesStats);
        cacheEntitiesStats.forEach((k, v) -> {
            logger.info("  " + k);
            v.forEach((k1, v1) -> {
                logger.info("    " + k1 + ": " + v1);
            });
        });

    }

    private void emitMessages(Integer numberOfMessages, String loggerName) {
        Map<String, Object> mdc = Collections
                .singletonMap("processContext", LongBase32IdConverter.toString(Sequence.nextId()));
        for (int count = 0; count < numberOfMessages; count++) {
            Map<String, Object> record = new HashMap<>();
            record.put("@timestamp", "2022-05-18T15:20:47.536Z");
            record.put("sequence", count);
            record.put("level", "INFO");
            record.put("loggerName", loggerName);
            record.put("message", "Me message.");
            record.put("mdc", mdc);

            try {
                String data = mapper.writeValueAsString(record);
                logger.debug("Sending: {}", data);
                emitter.send(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

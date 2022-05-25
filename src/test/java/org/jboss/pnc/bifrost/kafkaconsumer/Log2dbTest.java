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
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.pnc.bifrost.source.db.LogRecord;
import org.jboss.pnc.bifrost.test.DbUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Slf4j
@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
public class Log2dbTest {

    @Inject
    ObjectMapper mapper;

    @Inject
    @Channel("logs")
    Emitter<String> emitter;

    @Inject
    StoredCounter storedCounter;

    @Inject
    DbUtils dbUtils;

    @Test
    @Timeout(10)
    public void logsShouldBeStored() throws InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        storedCounter.addIncrementListener(count -> {
            if (count == 10) {
                semaphore.release();
            }
        });
        emitMessages(10, "org.apache");
        emitMessages(10, "org.jboss.pnc");
        semaphore.acquire();
        List<LogRecord> stored = LogRecord.listAll();
        Assertions.assertEquals(10, stored.size());
        stored.forEach(r -> {
            if (r.getLoggerName().startsWith("org.apache")) {
                Assertions.fail("LoggerName org.apache should not be stored.");
            }
        });
    }

    private void emitMessages(Integer numberOfMessages, String loggerName) {
        for (int count = 0; count < numberOfMessages; count++) {
            Map<String, Object> record = new HashMap<>();
            record.put("@timestamp", "2022-05-18T15:20:47.536Z");
            record.put("sequence", count);
            record.put("level", "INFO");
            record.put("loggerName", loggerName);
            record.put("message", "Me message.");

            try {
                String data = mapper.writeValueAsString(record);
                log.debug("Sending: {}", data);
                emitter.send(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

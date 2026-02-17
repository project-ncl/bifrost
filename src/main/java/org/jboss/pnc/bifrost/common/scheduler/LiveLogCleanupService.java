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

import io.quarkus.panache.common.Page;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import net.javacrumbs.shedlock.cdi.SchedulerLock;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.bifrost.source.db.LogLine;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;

@ApplicationScoped
@Slf4j
public class LiveLogCleanupService {

    @ConfigProperty(name = "log.cleanup.batch-size", defaultValue = "10000")
    int batchSize;

    @ConfigProperty(name = "log.cleanup.delete-after", defaultValue = "P2M")
    Period deleteAfter;

    @ConfigProperty(name = "log.cleanup.logger-names", defaultValue = "")
    List<String> loggerNames;

    @Scheduled(cron = "0 0 12 ? * SUN")
    @SchedulerLock(name = "liveLogCleanup", lockAtMostFor = "PT4H")
    public void cleanupLiveLogs() {
        OffsetDateTime thresholdDate = OffsetDateTime.now().minus(deleteAfter);

        long totalDeletedCount = 0;
        long deletedInBatchCount = 0;

        log.info("Starting to delete live log lines older than {}", thresholdDate);

        do {
            deletedInBatchCount = deleteBatch(thresholdDate);
            totalDeletedCount += deletedInBatchCount;

            log.info(
                    "Deleted batch of live log lines older than {} (batch size {}, deleted {} as of now in total)",
                    thresholdDate,
                    deletedInBatchCount,
                    totalDeletedCount);
        } while (deletedInBatchCount > 0);

        log.info("Deleted {} live log lines older than {}", totalDeletedCount, thresholdDate);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long deleteBatch(OffsetDateTime thresholdDate) {
        if (loggerNames.isEmpty()) {
            return 0;
        }

        List<Long> idsToBeDeleted = LogLine
                .find(
                        "select id from LogLine where eventTimestamp < ?1 and loggerName in ?2",
                        thresholdDate,
                        loggerNames)
                .project(Long.class)
                .page(Page.ofSize(batchSize))
                .list();

        if (idsToBeDeleted.isEmpty()) {
            return 0;
        }

        return LogLine.delete("id in ?1", idsToBeDeleted);
    }
}

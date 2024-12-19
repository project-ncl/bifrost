/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.bifrost.source.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class FinalLog extends PanacheEntityBase {

    @Id
    public long id;

    @ManyToOne(optional = false)
    public LogEntry logEntry;

    @Column(nullable = false)
    public OffsetDateTime eventTimestamp;

    @Column(nullable = false)
    public String loggerName;

    @Column(length = 32, nullable = false)
    public String md5sum;

    @ElementCollection
    @OnDelete(action = OnDeleteAction.CASCADE) // TODO remove with JoinColumn on Hibernate version 6+ (ticket: HHH-5529)
    public Set<String> tags;

    @Lob
    public Blob logContent;

    public long size;

    public static void copyFinalLogsToOutputStream(long processContext, String tag, OutputStream outputStream)
            throws SQLException, IOException {
        Collection<FinalLog> logs = getFinalLogsWithoutPreviousRetries(processContext, tag);

        // write all those logs to the output stream now.
        for (FinalLog finalLog : logs) {
            finalLog.logContent.getBinaryStream().transferTo(outputStream);
        }
    }

    /**
     * Get all the FinalLog objects for a particular process context and tag If multiple entries exist for a particular
     * process context, tag, and loggerName, the last entry for that loggerName is picked up. The multiple entries
     * happen when retries happen for a component, and we only want to show the last attempt in the logs.
     *
     * @param processContext process context to find
     * @param tag tag of the final log
     * @return collection of final log objects
     */
    public static Collection<FinalLog> getFinalLogsWithoutPreviousRetries(long processContext, String tag) {
        List<LogEntry> logEntries = LogEntry.list("processContext", processContext);

        // find all logs for this process context
        List<FinalLog> finalLogs = list("logEntry in ?1", Sort.by("eventTimestamp"), logEntries);

        // narrow it down to the specific tag
        finalLogs = finalLogs.stream().filter(a -> a.tags.contains(tag)).collect(Collectors.toList());

        // use LinkedHashMap to preserve order of insertion
        LinkedHashMap<String, FinalLog> logMap = new LinkedHashMap<>();

        // iterate through the finalLogs in order of eventTimestamp, and only keep the last final log for a loggername,
        // to get rid of previous retries final logs
        for (FinalLog finalLog : finalLogs) {
            logMap.put(finalLog.loggerName, finalLog);
        }
        return logMap.values();
    }

    public static long deleteByProcessContext(long processContext, String tag, boolean temporaryOnly) {
        // language=HQL
        String logEntryQuery = "select id from LogEntry where processContext = :processContext";
        Parameters parameters = Parameters.with("processContext", processContext);
        if (temporaryOnly) {
            logEntryQuery += " and temporary = :temporary";
            parameters.and("temporary", true);
        }

        // unfortunately JPA/HQL DELETE queries do not support Joins in FROM clause, so we have fallback to subquery
        // in WHERE clause
        // language=HQL
        String query = "from FinalLog where logEntry.id in (" + logEntryQuery + ")";
        if (tag != null) {
            query += " and :tag in elements(tags)";
            parameters.and("tag", tag);
        }

        return delete(query, parameters);
    }
}

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
package org.jboss.pnc.bifrost.source.db;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Parameters;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.NamedQuery;
import javax.persistence.QueryHint;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.Optional;

@EqualsAndHashCode(exclude = "id")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@ToString
@Table(
        indexes = {
                @Index(name = "idx_logentry_processContext", columnList = "processContext"),
                @Index(name = "idx_logentry_processContextVariant", columnList = "processContextVariant"),
                @Index(name = "idx_logentry_requestContext", columnList = "requestContext"),
                @Index(name = "idx_logentry_buildId", columnList = "buildId") })
@NamedQuery(
        name = "LogEntry.findExisting",
        query = "FROM LogEntry e" + " WHERE e.processContext = :processContext"
                + "   AND e.processContextVariant = :processContextVariant"
                + "   AND e.requestContext = :requestContext" + "   AND e.temporary = :temporary"
                + "   AND e.buildId = :buildId",
        hints = @QueryHint(name = "org.hibernate.cacheable", value = "true"))
@Cacheable
public class LogEntry extends PanacheEntityBase {

    @Id
    long id;

    @NotNull
    @Column(nullable = false)
    Long processContext;

    @Column(length = 10)
    String processContextVariant;

    @Column(length = 32)
    String requestContext;

    Boolean temporary;

    Long buildId;

    public static Optional<LogEntry> findExisting(LogEntry logEntry) {
        Parameters parameters = Parameters.with("processContext", logEntry.processContext)
                .and("processContextVariant", logEntry.processContextVariant)
                .and("requestContext", logEntry.requestContext)
                .and("temporary", logEntry.temporary)
                .and("buildId", logEntry.buildId);
        return find("#LogEntry.findExisting", parameters).firstResultOptional();
    }

}

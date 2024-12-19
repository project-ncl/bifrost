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

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

@EqualsAndHashCode(exclude = "id", callSuper = false)
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
        StringBuilder query = new StringBuilder("processContext = :processContext");
        Parameters parameters = Parameters.with("processContext", logEntry.processContext);

        parameters = handleNullField(logEntry.processContextVariant, "processContextVariant", query, parameters);
        parameters = handleNullField(logEntry.requestContext, "requestContext", query, parameters);
        parameters = handleNullField(logEntry.temporary, "temporary", query, parameters);
        parameters = handleNullField(logEntry.buildId, "buildId", query, parameters);

        return find(query.toString(), parameters).withHint("org.hibernate.cacheable", true).firstResultOptional();
    }

    private static Parameters handleNullField(
            Object field,
            String fieldName,
            StringBuilder query,
            Parameters parameters) {
        if (field == null) {
            query.append(" and ").append(fieldName).append(" is null");
        } else {
            query.append(" and ").append(fieldName).append(" = :").append(fieldName);
            parameters = parameters.and(fieldName, field);
        }
        return parameters;
    }

    public static boolean isPresent(Long processContext) {
        return count("processContext = :processContext", Parameters.with("processContext", processContext)) > 0;
    }
}

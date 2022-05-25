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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Type;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@ToString
@Table(
        indexes = {
                @Index(name = "idx_timestamp", columnList = "timestamp"),
                @Index(name = "idx_sequence", columnList = "sequence"),
                @Index(name = "idx_loggerName", columnList = "loggerName"),
                @Index(name = "idx_processContext", columnList = "processContext"),
                @Index(name = "idx_processContextVariant", columnList = "processContextVariant"),
                @Index(name = "idx_requestContext", columnList = "requestContext") })
@JsonDeserialize(using = LogRecordDeserializer.class)
public class LogRecord extends PanacheEntityBase {

    @Id
    long id;

    Instant timestamp;

    int sequence;

    LogLevel level;

    String loggerName;

    @Lob
    @Type(type = "org.hibernate.type.TextType")
    String logLine;

    Long processContext;

    Long processContextVariant;

    String requestContext;

    Boolean temp;

    Long buildId;

}

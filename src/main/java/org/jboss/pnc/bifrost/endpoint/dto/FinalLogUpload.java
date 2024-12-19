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
package org.jboss.pnc.bifrost.endpoint.dto;

import lombok.Data;
import org.jboss.resteasy.reactive.PartType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.FormParam;
import java.io.InputStream;
import java.time.OffsetDateTime;

@Data
public class FinalLogUpload {

    @FormParam("md5sum")
    @PartType("text/plain")
    @NotBlank
    private String md5sum;

    @FormParam("endTime")
    @PartType("text/plain")
    @NotNull
    private OffsetDateTime endTime;

    @FormParam("loggerName")
    @PartType("text/plain")
    @NotBlank
    private String loggerName;

    @FormParam("tag")
    @PartType("text/plain")
    @NotEmpty
    private String tag;

    @FormParam("logfile")
    // @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream logfile;
}

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
package org.jboss.pnc.bifrost.endpoint;

import com.sun.jdi.ThreadReference;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.MemorySize;
import io.quarkus.security.Authenticated;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.engine.jdbc.BlobProxy;
import org.jboss.pnc.api.constants.MDCHeaderKeys;
import org.jboss.pnc.bifrost.common.ChecksumValidatingStream;
import org.jboss.pnc.bifrost.endpoint.dto.FinalLogUpload;
import org.jboss.pnc.bifrost.source.db.FinalLog;
import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.bifrost.source.db.LogEntryRepository;
import org.jboss.pnc.bifrost.source.db.converter.ValueConverter;
import org.jboss.pnc.bifrost.source.db.converter.idConverter;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Path("/final-log")
@PermitAll
@Slf4j
public class LogUpload {
    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    MemorySize maxPostValue;

    @Inject
    LogEntryRepository logEntryRepository;

    private final ValueConverter<Long> idConverter = new idConverter();

    @Path("/upload")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public String uploadFinalLog(@Valid FinalLogUpload logUpload, @Context HttpHeaders headers) {
        Log.info("Receiving logfile");

        FinalLog finalLog = new FinalLog();
        finalLog.logEntry = getLogEntry(headers);
        finalLog.eventTimestamp = logUpload.getEndTime();
        finalLog.loggerName = logUpload.getLoggerName();
        finalLog.md5sum = logUpload.getMd5sum();

        if (logUpload.getTag() != null) {
            finalLog.tags = Set.of(logUpload.getTag().split(","));
        }

        ChecksumValidatingStream stream = ChecksumValidatingStream
                .validate(logUpload.getLogfile(), logUpload.getMd5sum());

        // Configure the proxy to read up to the max body post size. The proxy behaves well if the input
        // stream size is less than that size
        finalLog.logContent = BlobProxy.generateProxy(stream, maxPostValue.asLongValue());
        finalLog.persistAndFlush();
        try {
            stream.validate();
        } catch (ValidationException ex) {
            throw new BadRequestException("The uploaded log has wrong checksums: " + ex.getMessage(), ex);
        }

        FinalLog.getEntityManager().refresh(finalLog);
        finalLog.size = stream.readSize();

        return "ok";
    }

    @Path("/{processContext}/delete")
    @DELETE
    @RolesAllowed("**") // FIXME change to specific allowed roles
    public Response deleteFinalLog(@PathParam("processContext") String processContext) {
        // parse process context
        long processContextLong;
        if (processContext.startsWith("build-")) {
            processContextLong = idConverter.convert(processContext);
        } else {
            try {
                processContextLong = Long.parseLong(processContext);
            } catch (NumberFormatException e) {
                throw new BadRequestException(
                        "Process context " + processContext + "is not a number nor a Build process.");
            }
        }
        List<LogEntry> logEntries = LogEntry.list("processContext", processContextLong);
        if (logEntries.stream().noneMatch(LogEntry::getTemporary)) {
            throw new BadRequestException("Can't delete logs of persistent entries.");
        }

        log.debug("Deleting final logs with processContext: {} (long -> {})", processContext, processContextLong);
        long deleted = FinalLog.deleteByProcessContext(processContextLong, null, true);
        log.debug("Deleted {} rows with processContext {} (long -> {}).", deleted, processContext, processContextLong);

        return Response.noContent().build();
    }

    private LogEntry getLogEntry(HttpHeaders headers) {
        LogEntry logEntry = new LogEntry();
        String processContext = headers.getHeaderString(MDCHeaderKeys.PROCESS_CONTEXT.getHeaderName());
        if (processContext == null) {
            throw new BadRequestException("Header " + MDCHeaderKeys.PROCESS_CONTEXT.getHeaderName() + " required.");
        }
        logEntry.setProcessContext(idConverter.convert(processContext));
        logEntry.setProcessContextVariant(
                headers.getHeaderString(MDCHeaderKeys.PROCESS_CONTEXT_VARIANT.getHeaderName()));
        logEntry.setTemporary(Boolean.valueOf(headers.getHeaderString(MDCHeaderKeys.TMP.getHeaderName())));
        logEntry.setRequestContext(headers.getHeaderString(MDCHeaderKeys.REQUEST_CONTEXT.getHeaderName()));
        return logEntryRepository.get(logEntry);
    }
}

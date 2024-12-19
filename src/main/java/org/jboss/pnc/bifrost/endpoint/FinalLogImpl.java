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

import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.MemorySize;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.engine.jdbc.BlobProxy;
import org.jboss.pnc.api.bifrost.rest.FinalLogRest;
import org.jboss.pnc.api.constants.MDCHeaderKeys;
import org.jboss.pnc.bifrost.common.ChecksumValidatingStream;
import org.jboss.pnc.bifrost.endpoint.dto.FinalLogUpload;
import org.jboss.pnc.bifrost.source.db.FinalLog;
import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.bifrost.source.db.LogEntryRepository;
import org.jboss.pnc.bifrost.source.db.converter.ValueConverter;
import org.jboss.pnc.bifrost.source.db.converter.IdConverter;
import org.jboss.pnc.common.concurrent.Sequence;
import org.jboss.pnc.common.pnc.LongBase32IdConverter;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

@PermitAll
@Slf4j
public class FinalLogImpl implements FinalLogRest {
    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    MemorySize maxPostValue;

    @Inject
    LogEntryRepository logEntryRepository;

    private final ValueConverter<Long> idConverter = new IdConverter();

    @Path("/upload")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({ "pnc-app-bifrost-final-log-create", "pnc-users-admin" })
    @Transactional
    public String uploadFinalLog(@Valid FinalLogUpload logUpload, @Context HttpHeaders headers) {
        Log.info("Receiving logfile");

        FinalLog finalLog = new FinalLog();
        finalLog.id = Sequence.nextId();
        finalLog.logEntry = getLogEntry(headers);
        finalLog.eventTimestamp = logUpload.getEndTime();
        finalLog.loggerName = logUpload.getLoggerName();
        finalLog.md5sum = logUpload.getMd5sum();

        if (logUpload.getTag() != null) {
            finalLog.tags = Set.of(logUpload.getTag().split(","));
        }

        long logUploadStarted = System.currentTimeMillis();
        ChecksumValidatingStream stream = ChecksumValidatingStream
                .validate(logUpload.getLogfile(), logUpload.getMd5sum());

        // Configure the proxy to read up to the max body post size. The proxy behaves well if the input
        // stream size is less than that size
        finalLog.logContent = BlobProxy.generateProxy(stream, maxPostValue.asLongValue());
        finalLog.persistAndFlush();
        long logUploadEnded = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            long duration = logUploadEnded - logUploadStarted;
            long logSize = stream.readSize();
            log.debug(
                    "Log sized {} KB uploaded in {} s {} ms, that is {} KB/s",
                    logSize / 1000,
                    duration / 1000,
                    duration % 1000,
                    logSize / duration);
        }
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
    @RolesAllowed({ "pnc-app-bifrost-final-log-delete", "pnc-users-admin" })
    @Transactional // FIXME change to specific allowed roles
    public Response deleteFinalLog(@PathParam("processContext") String processContext) {
        // parse process context
        Long processContextLong = idConverter.convert(processContext);
        if (processContextLong == null) {
            throw new BadRequestException("Process context " + processContext + "is not a number nor a Build process.");
        }

        List<LogEntry> logEntries = LogEntry.list("processContext", processContextLong);
        if (logEntries.isEmpty()) {
            return Response.noContent().build();
        }
        if (logEntries.stream().noneMatch(LogEntry::getTemporary)) {
            throw new BadRequestException("Can't delete logs of persistent entries.");
        }

        log.debug("Deleting final logs with processContext: {} (long -> {})", processContext, processContextLong);
        long deleted = FinalLog.deleteByProcessContext(processContextLong, null, true);
        log.debug("Deleted {} rows with processContext {} (long -> {}).", deleted, processContext, processContextLong);

        return Response.noContent().build();
    }

    @Override
    @Transactional
    public Response getFinalLog(@PathParam("buildId") String buildId, @PathParam("tag") String tag) {
        Long context = LongBase32IdConverter.toLong(buildId);
        // if context is not present, return status 404
        if (!LogEntry.isPresent(context)) {
            return Response.status(404).build();
        }

        // if logs are not present, return status 204
        if (FinalLog.getFinalLogsWithoutPreviousRetries(context, tag).isEmpty()) {
            return Response.noContent().build();
        }

        // at this point, we are running in a transaction. We copy the blob content to a file
        File tempFile;
        try {
            tempFile = File.createTempFile("final-log", ".tmp");

            try (FileOutputStream fout = new FileOutputStream(tempFile)) {
                FinalLog.copyFinalLogsToOutputStream(LongBase32IdConverter.toLong(buildId), tag, fout);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return Response.ok().entity((StreamingOutput) output -> {
            // at this point, we are not running in a transaction. We can however stream the content of the file to
            // the output
            try (FileInputStream fin = new FileInputStream(tempFile)) {
                fin.transferTo(output);
            }
            // cleanup
            tempFile.delete();
        }).build();
    }

    @Override
    public long getFinalLogSize(@PathParam("buildId") String buildId, @PathParam("tag") String tag) {
        return FinalLog.getFinalLogsWithoutPreviousRetries(LongBase32IdConverter.toLong(buildId), tag)
                .stream()
                .mapToLong(m -> m.size)
                .sum();
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

/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2020 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.bifrost.endpoint.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

@Provider
public class AllExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger log = LoggerFactory.getLogger(AllExceptionMapper.class);

    @Inject
    ObjectMapper mapper;

    @Override
    public Response toResponse(Exception e) {
        int responseCode = INTERNAL_SERVER_ERROR.getStatusCode();

        if (e instanceof WebApplicationException) {
            responseCode = ((WebApplicationException) e).getResponse().getStatus();
            if (e instanceof NotFoundException) {
                log.info("Resource requested by a client was not found.", e);
                // return with empty body
                return ((WebApplicationException) e).getResponse();
            } else if (e instanceof ForbiddenException) {
                log.warn("Access to a resource requested by a client has been forbidden.", e);
            } else if (e instanceof NotAllowedException) {
                log.warn("Client requesting a resource method that is not allowed.", e);
            } else if (e instanceof NotAuthorizedException) {
                log.warn("Request authorization failure.", e);
            } else {
                log.warn("WebApplicationException occurred when processing REST response", e);
            }
        } else {
            log.error("An exception occurred.", e);
        }

        try {
            return Response.status(responseCode)
                    .entity(mapper.writeValueAsString(e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (JsonProcessingException ex) {
            String message = "Cannot serialize exception message.";
            log.error(message, e);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity("{'" + message + "'}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}

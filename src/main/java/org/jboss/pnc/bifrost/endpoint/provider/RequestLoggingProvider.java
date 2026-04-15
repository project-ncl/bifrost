package org.jboss.pnc.bifrost.endpoint.provider;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

@Slf4j
public class RequestLoggingProvider {
    private static final String REQUEST_EXECUTION_START = "request-execution-start";

    @ServerRequestFilter
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(REQUEST_EXECUTION_START, System.currentTimeMillis());

        UriInfo uriInfo = requestContext.getUriInfo();
        Request request = requestContext.getRequest();

        log.info("Requested {} {}.", request.getMethod(), uriInfo.getRequestUri());

    }

    @ServerResponseFilter
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Long startTime = (Long) requestContext.getProperty(REQUEST_EXECUTION_START);

        String took;
        if (startTime == null) {
            took = "-1";
        } else {
            took = Long.toString(System.currentTimeMillis() - startTime);
        }
        log.info(
                "Request {} completed with status {} and took {}ms",
                requestContext.getUriInfo().getPath(),
                responseContext.getStatus(),
                took);
    }
}

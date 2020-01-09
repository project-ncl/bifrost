package org.jboss.pnc.bifrost.endpoint;

import org.jboss.pnc.bifrost.source.dto.Direction;
import org.jboss.pnc.bifrost.source.dto.Line;
import org.jboss.pnc.bifrost.source.dto.MetaData;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
public interface Rest {

    @GET
    @Path("/text")
    @Produces(MediaType.TEXT_PLAIN)
    Response getAllLines(
            @QueryParam("matchFilters") String matchFilters,
            @QueryParam("prefixFilters") String prefixFilters,
            @QueryParam("afterLine") Line afterLine,
            @QueryParam("direction") Direction direction,
            @QueryParam("maxLines") Integer maxLines,
            @QueryParam("follow") boolean follow,
            @QueryParam("timeoutProbeString") String timeoutProbeString); //if string is defined the server is sending given string as a connection probe. The string is printed with the logs.

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    List<Line> getLines(
            @QueryParam("matchFilters") String matchFilters,
            @QueryParam("prefixFilters") String prefixFilters,
            @QueryParam("afterLine") Line afterLine,
            @QueryParam("direction") Direction direction,
            @QueryParam("maxLines") Integer maxLines)
            throws IOException;

    @GET
    @Path("/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    MetaData getMetaData(
            @QueryParam("matchFilters") String matchFilters,
            @QueryParam("prefixFilters") String prefixFilters,
            @QueryParam("afterLine") Line afterLine,
            @QueryParam("direction") Direction direction,
            @QueryParam("maxLines") Integer maxLines)
            throws IOException;

    @GET
    @Path("/test/ready")
    @Produces(MediaType.APPLICATION_JSON)
    Response readinessProbe();

    @GET
    @Path("/test/status")
    @Produces(MediaType.APPLICATION_JSON)
    Response livenessProbe();

}

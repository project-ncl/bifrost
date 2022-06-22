package org.jboss.pnc.bifrost.endpoint;

import com.google.common.collect.Lists;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.vertx.core.impl.ConcurrentHashSet;
import org.jboss.logging.Logger;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.common.DateUtil;
import org.jboss.pnc.bifrost.source.db.LogEntry;
import org.jboss.pnc.bifrost.source.db.LogLevel;
import org.jboss.pnc.bifrost.source.db.LogLine;
import org.jboss.pnc.common.concurrent.Sequence;
import org.jboss.pnc.common.pnc.LongBase32IdConverter;

import javax.inject.Inject;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@Path("/test")
public class MyEndpoint {

    private static final Logger logger = Logger.getLogger(MyEndpoint.class);

    @Inject
    RestImpl bifrost;

    @Inject
    TransactionManager manager;

    @Inject
    MrBean mrBean;

    @POST
    // comma seperated processContexts e.g. 23,45,12312312,5435345
    public void endpoint(String contexts) throws IOException, SystemException {
        logger.info("GOT request for " + contexts);
        String[] logContextStrings = contexts.split(",");

        // partition into sizes of 100
        List<List<String>> listOfPartitions = Lists.partition(Arrays.asList(logContextStrings), 100);
        List<String> logContextQuery = listOfPartitions.stream()
                .map(
                        partition -> partition.stream()
                                // filter out processContexts which are already saved
                                .filter(
                                        processContext -> LogEntry
                                                .find("processContext", Long.parseLong(processContext))
                                                .firstResultOptional()
                                                .isEmpty())
                                // reduce to query string
                                .reduce((c1, c2) -> c1 + '|' + c2))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());

        if (logContextQuery.isEmpty()) {
            logger.info("NOTHING TO QUERY");
            return;
        }

        //make the requests parallel
        logContextQuery.parallelStream().forEach((query) -> {
            logger.info("processing " + query);
            try {
                mrBean.queryAndSave(query);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        logger.info("END");
    }

    @POST
    @Path("/long/{value}")
    public Long convert(@PathParam("value") String value) {
        logger.info("got " + value);
        return LongBase32IdConverter.toLong(value);
    }

    @POST
    @Path("/base/{value}")
    public String convert(@PathParam("value") Long value) {
        return LongBase32IdConverter.toString(value);
    }
}

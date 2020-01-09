package org.jboss.pnc.bifrost.endpoint;

import org.jboss.logging.Logger;
import org.jboss.pnc.bifrost.common.Reference;
import org.jboss.pnc.bifrost.common.scheduler.Subscription;
import org.jboss.pnc.bifrost.common.scheduler.TimeoutExecutor;
import org.jboss.pnc.bifrost.endpoint.provider.DataProvider;
import org.jboss.pnc.bifrost.source.dto.Direction;
import org.jboss.pnc.bifrost.source.dto.Line;
import org.jboss.pnc.bifrost.source.dto.MetaData;
import org.jboss.pnc.common.security.Md5;

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Path("/")
public class RestImpl implements Rest {

    private static Logger logger = Logger.getLogger(RestImpl.class);

    @Inject
    DataProvider dataProvider;

    private Map<String, ScheduledThreadPoolExecutor> probeExecutor = new ConcurrentHashMap<>();

    @Override
    public Response getAllLines(
            String matchFilters,
            String prefixFilters,
            Line afterLine,
            Direction direction,
            Integer maxLines,
            boolean follow,
            String timeoutProbeString) {

        ArrayBlockingQueue<Optional<Line>> queue = new ArrayBlockingQueue(1024); //TODO

        Runnable addEndOfDataMarker = () -> {
            try {
                queue.offer(Optional.empty(), 5, TimeUnit.SECONDS); //TODO
            } catch (InterruptedException e) {
                logger.error("Cannot add end of data marker.", e);
            }
        };

        Subscription subscription = new Subscription(addEndOfDataMarker);

        StreamingOutput stream = outputStream -> {

            Reference<TimeoutExecutor.Task> timeoutProbeTask = new Reference<>();
            if (follow && timeoutProbeString != null && !timeoutProbeString.equals("")) {
                TimeoutExecutor timeoutExecutor = new TimeoutExecutor(getExecutorService());
                Runnable sendProbe = () -> {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                    try {
                        writer.write(timeoutProbeString);
                        writer.flush();
                    } catch (IOException e) {
                        timeoutProbeTask.get().cancel();
                        logger.warn("Cannot send connection probe, client might closed the connection.", e);
                        complete(subscription, outputStream);
                    }
                };
                timeoutProbeTask.set(timeoutExecutor.submit(sendProbe, 15000, TimeUnit.MICROSECONDS));
            }

            while (true) {
                try {
                    Optional<Line> maybeLine = queue.take();
                    if (maybeLine.isPresent()) {
                        Line line = maybeLine.get();
                        logger.trace("Sending line: " + line.asString());
                        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                        writer.write(line.asString() + "\n");
                        writer.flush();
                        if (line.isLast() && follow == false) { //when follow is true, the connection must be terminated from the client side
                            timeoutProbeTask.ifPresent(t -> t.cancel());
                            complete(subscription, outputStream);
                            break;
                        }
                        timeoutProbeTask.ifPresent(t -> t.update());
                    } else { //empty line indicating end of results
                        logger.info("Closing connection, no results.");
                        timeoutProbeTask.ifPresent(t -> t.cancel());
                        complete(subscription, outputStream);
                        break;
                    }
                } catch (IOException e) {
                    logger.warn("Cannot write output. Client might closed the connection. Unsubscribing ... " + e.getMessage());
                    timeoutProbeTask.ifPresent(t -> t.cancel());
                    complete(subscription, outputStream);
                    break;
                } catch (InterruptedException e) {
                    logger.error("Cannot read from queue.", e);
                    timeoutProbeTask.ifPresent(t -> t.cancel());
                    complete(subscription, outputStream);
                    break;
                }
            }
        };

        int[] receivedLines = {0};
        Consumer<Line> onLine = line -> {
            try {
                if (line != null) {
                    logger.trace("Adding line to output queue: " + line.asString());
                    queue.offer(Optional.of(line), 5, TimeUnit.SECONDS); //TODO
                    receivedLines[0]++;

                    if (maxLines != null && receivedLines[0] >= maxLines) {
                        logger.debug("Received max lines, unsubscribing ...");
                        addEndOfDataMarker.run();
                        dataProvider.unsubscribe(subscription);
                    }
                } else {
                    logger.debug("Received null line.");
                }

                if (follow == false && (line == null || line.isLast())) {
                    logger.debug("Received last line or no results, unsubscribing and closing ...");
                    //signal connection close
                    addEndOfDataMarker.run();
                    dataProvider.unsubscribe(subscription);
                }
            } catch (Exception e) {
                logger.warn("Unsubscribing due to the exception.", e);
                addEndOfDataMarker.run();
                dataProvider.unsubscribe(subscription);
            }
        };
        dataProvider.subscribe(
                matchFilters,
                prefixFilters,
                Optional.ofNullable(afterLine),
                onLine,
                subscription,
                Optional.ofNullable(maxLines)
        );
        return Response.ok(stream).build();
    }

    private ScheduledThreadPoolExecutor getExecutorService() {
        return probeExecutor.computeIfAbsent("INSTANCE", k -> new ScheduledThreadPoolExecutor(1));
    }

    private void complete(Subscription subscription, OutputStream outputStream) {
        dataProvider.unsubscribe(subscription);
        try {
            outputStream.close();
        } catch (IOException e) {
            logger.warn("Cannot close output stream.", e);
        }
    }

    @Override
    public List<Line> getLines(String matchFilters, String prefixFilters, Line afterLine, Direction direction, Integer maxLines)
            throws IOException {
        List<Line> lines = new ArrayList<>();
        Consumer<Line> onLine = line -> lines.add(line);
        dataProvider.get(
                matchFilters,
                prefixFilters,
                Optional.ofNullable(afterLine),
                direction,
                Optional.ofNullable(maxLines),
                onLine);
        return lines;
    }

    @Override
    public MetaData getMetaData(String matchFilters,
            String prefixFilters,
            Line afterLine,
            Direction direction,
            Integer maxLines) throws IOException {

        try {
            Md5 md5 = new Md5();
            Consumer<Line> onLine = line -> {
                try {
                    md5.add(line.getMessage());
                } catch (UnsupportedEncodingException e) {
                    logger.error(e);
                }
            };
            dataProvider.get(
                    matchFilters,
                    prefixFilters,
                    Optional.ofNullable(afterLine),
                    direction,
                    Optional.ofNullable(maxLines),
                    onLine);
            return new MetaData(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            logger.error(e);
            throw new ServerErrorException(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }

    }

    @Override
    public Response readinessProbe() {
        return Response.ok().build();
    }

    @Override
    public Response livenessProbe() {
        return Response.ok().build(); //TODO test ES connection
    }

}

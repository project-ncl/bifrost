package org.jboss.pnc.bifrost.endpoint.websocket;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.beanutils.BeanUtils;
import org.jboss.logging.Logger;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.bifrost.endpoint.provider.DataProviderMock;
import org.jboss.pnc.bifrost.mock.LineProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@QuarkusTest
public class SubscriptionTest {

    private static Logger logger = Logger.getLogger(SubscriptionTest.class);

    private static final CompletableFuture<Boolean> connected = new CompletableFuture<>();
    private static final LinkedBlockingDeque<Result> RESULTS = new LinkedBlockingDeque<>();
    private static final LinkedBlockingDeque<Line> LINES = new LinkedBlockingDeque<>();

    @Inject
    DataProviderMock mockDataProvider;

    @TestHTTPResource("/socket")
    URI uri;

    @Test
    public void testWebsocketGetLines() throws Exception {
        try(Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
            connected.get(10, TimeUnit.SECONDS);

            MethodGetLines methodGetLines = new MethodGetLines();
            GetLinesDto parameters = new GetLinesDto();
            parameters.setMatchFilters("");
            parameters.setPrefixFilters("");
            Map<String, Object> parameterMap = (Map)BeanUtils.describe(parameters);
            JSONRPC2Request request = new JSONRPC2Request(methodGetLines.getName(), parameterMap, Integer.valueOf(1));
            session.getAsyncRemote().sendText(request.toJSONString());

            //should receive RCP response
            Result response = RESULTS.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response);

            //should receive 5 lines
            int received = 0;
            while (received < 5) {
                Line receivedLine = LINES.poll(10, TimeUnit.SECONDS);
                logger.debug("Received line notification: " + receivedLine.getMessage());
                if (receivedLine == null) {
                    break;
                }
                received++;
            }
            session.close();
            Assertions.assertEquals(5, received);
        }
        TimeUnit.MILLISECONDS.sleep(250); //wait clean for shutdown
    }

    @Test
    public void testWebsocketSubscription() throws Exception {
        List<Line> lines = LineProducer.getLines(5, "some-ctx");
        mockDataProvider.addAllLines(lines);

        try(Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
            connected.get(10, TimeUnit.SECONDS);

            MethodSubscribe methodSubscribe = new MethodSubscribe();
            SubscribeDto parameters = new SubscribeDto();
            parameters.setMatchFilters("");
            parameters.setPrefixFilters("");
            Map<String, Object> parameterMap = (Map)BeanUtils.describe(parameters);
            JSONRPC2Request request = new JSONRPC2Request(methodSubscribe.getName(), parameterMap, Integer.valueOf(2));
            session.getAsyncRemote().sendText(request.toJSONString());

            Result result = RESULTS.poll(5, TimeUnit.SECONDS);
            Assertions.assertTrue(result instanceof SubscribeResultDto);

            //should receive 5 lines
            int received = 0;
            while (received < 5) {
                Line receivedLine = LINES.poll(10, TimeUnit.SECONDS);
                if (receivedLine == null) {
                    break;
                }
                String message = receivedLine.getMessage();
                logger.debug("Received line notification: " + message);
                Assertions.assertTrue(StringUtils.isNotBlank(message));
                received++;
            }
            session.close();
            Assertions.assertEquals(5, received);
        }
        TimeUnit.MILLISECONDS.sleep(250); //wait clean for shutdown
    }

    @Test
    public void testGetAllAndFollow() throws Exception {
        List<Line> lines = LineProducer.getLines(5, "some-ctx");
        mockDataProvider.addAllLines(lines);

        try(Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
            connected.get(10, TimeUnit.SECONDS);

            MethodSubscribe methodSubscribe = new MethodSubscribe();
            SubscribeDto parameters = new SubscribeDto();
            parameters.setMatchFilters("");
            parameters.setPrefixFilters("");
            Map<String, Object> parameterMap = (Map)BeanUtils.describe(parameters);
            JSONRPC2Request request = new JSONRPC2Request(methodSubscribe.getName(), parameterMap, Integer.valueOf(2));
            session.getAsyncRemote().sendText(request.toJSONString());

            Result result = RESULTS.poll(5, TimeUnit.SECONDS);
            Assertions.assertTrue(result instanceof SubscribeResultDto);

            //should receive 5 lines
            int received = 0;
            while (received < 5) {
                Line receivedLine = LINES.poll(10, TimeUnit.SECONDS);
                String message = receivedLine.getMessage();
                logger.debug("Received line notification: " + message);
                Assertions.assertTrue(StringUtils.isNotBlank(message));
                received++;
            }
            Assertions.assertEquals(5, received);

            //insert new lines
            Thread.sleep(750);
            List<Line> newLines = LineProducer.getLines(5, "some-ctx");
            mockDataProvider.addAllLines(newLines);

            //should receive 5 lines
            while (received < 10) {
                Line receivedLine = LINES.poll(10, TimeUnit.SECONDS);
                String message = receivedLine.getMessage();
                logger.debug("Received line notification: " + message);
                Assertions.assertTrue(StringUtils.isNotBlank(message));
                received++;
            }
            Assertions.assertEquals(10, received);
        }
        TimeUnit.MILLISECONDS.sleep(250); //wait clean for shutdown
    }

    @ClientEndpoint
    public static class Client {

        @OnOpen
        public void open(Session session) {
            logger.debug("Client connected.");
            connected.complete(true);
        }

        @OnMessage
        void message(String message, Session session) {
            logger.debug("Client received: " + message);
            Jsonb jsonb = JsonbBuilder.create();

            Map<String, Object> rpcResponse = jsonb.fromJson(
                    message,
                    Map.class);
            Map<String, Object> resultMap = (Map<String, Object>) rpcResponse.get("result");

            String type = (String) resultMap.get("type");
            if (OkResult.class.getCanonicalName().equals(type)) {
                OkResult result = new OkResult();
                RESULTS.add(result);
            } else if (SubscribeResultDto.class.getCanonicalName().equals(type)) {
                SubscribeResultDto result = new SubscribeResultDto((String) resultMap.get("value"));
                RESULTS.add(result);
            } else if (LineResult.class.getCanonicalName().equals(type)) {
                    Line line = new Line();
                try {
                    BeanUtils.populate(line, (Map<String, ? extends Object>) resultMap.get("value"));
                } catch (Exception e) {
                    logger.error("Unable to populate line bean.", e);
                    e.printStackTrace();
                }
                LINES.add(line);
            }
        }

        @OnMessage
        void message(ByteBuffer buffer, Session session) {
            String message = new String(buffer.array(), StandardCharsets.UTF_8);
            logger.debug("Client received binary message: " + message);

            Line line = Line.fromString(message);
            LINES.add(line);
        }
    }


    private static List<Line> parseLines(String json) {
        Jsonb jsonb = JsonbBuilder.create();
        Line[] lines = jsonb.fromJson(json, Line[].class);
        return Arrays.asList(lines);
    }
}

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
package org.jboss.pnc.bifrost.endpoint.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.beanutils.BeanUtils;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.bifrost.common.Json;
import org.jboss.pnc.bifrost.endpoint.provider.DataProviderMock;
import org.jboss.pnc.bifrost.mock.LineProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
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

    private static Logger logger = LoggerFactory.getLogger(SubscriptionTest.class);

    private static final CompletableFuture<Boolean> connected = new CompletableFuture<>();
    private static final LinkedBlockingDeque<Result> RESULTS = new LinkedBlockingDeque<>();
    private static final LinkedBlockingDeque<Line> LINES = new LinkedBlockingDeque<>();

    @Inject
    DataProviderMock mockDataProvider;

    @TestHTTPResource("/socket")
    URI uri;

    @Test
    public void testWebsocketGetLines() throws Exception {
        try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
            connected.get(10, TimeUnit.SECONDS);

            MethodGetLines methodGetLines = new MethodGetLines();
            GetLinesDto parameters = new GetLinesDto();
            parameters.setMatchFilters("");
            parameters.setPrefixFilters("");
            Map<String, Object> parameterMap = (Map) BeanUtils.describe(parameters);
            JSONRPC2Request request = new JSONRPC2Request(methodGetLines.getName(), parameterMap, Integer.valueOf(1));
            session.getAsyncRemote().sendText(request.toJSONString());

            // should receive RCP response
            Result response = RESULTS.poll(10, TimeUnit.SECONDS);
            Assertions.assertNotNull(response);

            // should receive 5 lines
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
        TimeUnit.MILLISECONDS.sleep(250); // wait clean for shutdown
    }

    @Test
    public void testWebsocketSubscription() throws Exception {
        List<Line> lines = LineProducer.getLines(5, "some-ctx");
        mockDataProvider.addAllLines(lines);

        try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
            connected.get(10, TimeUnit.SECONDS);

            MethodSubscribe methodSubscribe = new MethodSubscribe();
            SubscribeDto parameters = new SubscribeDto();
            parameters.setMatchFilters("");
            parameters.setPrefixFilters("");
            Map<String, Object> parameterMap = (Map) BeanUtils.describe(parameters);
            JSONRPC2Request request = new JSONRPC2Request(methodSubscribe.getName(), parameterMap, Integer.valueOf(2));
            session.getAsyncRemote().sendText(request.toJSONString());

            Result result = RESULTS.poll(5, TimeUnit.SECONDS);
            Assertions.assertTrue(result instanceof SubscribeResultDto);

            // should receive 5 lines
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
        TimeUnit.MILLISECONDS.sleep(250); // wait clean for shutdown
    }

    @Test
    public void testGetAllAndFollow() throws Exception {
        List<Line> lines = LineProducer.getLines(5, "some-ctx");
        mockDataProvider.addAllLines(lines);

        try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
            connected.get(10, TimeUnit.SECONDS);

            MethodSubscribe methodSubscribe = new MethodSubscribe();
            SubscribeDto parameters = new SubscribeDto();
            parameters.setMatchFilters("");
            parameters.setPrefixFilters("");
            Map<String, Object> parameterMap = (Map) BeanUtils.describe(parameters);
            JSONRPC2Request request = new JSONRPC2Request(methodSubscribe.getName(), parameterMap, Integer.valueOf(2));
            session.getAsyncRemote().sendText(request.toJSONString());

            Result result = RESULTS.poll(5, TimeUnit.SECONDS);
            Assertions.assertTrue(result instanceof SubscribeResultDto);

            // should receive 5 lines
            int received = 0;
            while (received < 5) {
                Line receivedLine = LINES.poll(10, TimeUnit.SECONDS);
                String message = receivedLine.getMessage();
                logger.debug("Received line notification: " + message);
                Assertions.assertTrue(StringUtils.isNotBlank(message));
                received++;
            }
            Assertions.assertEquals(5, received);

            // insert new lines
            Thread.sleep(750);
            List<Line> newLines = LineProducer.getLines(5, "some-ctx");
            mockDataProvider.addAllLines(newLines);

            // should receive 5 lines
            while (received < 10) {
                Line receivedLine = LINES.poll(10, TimeUnit.SECONDS);
                String message = receivedLine.getMessage();
                logger.debug("Received line notification: " + message);
                Assertions.assertTrue(StringUtils.isNotBlank(message));
                received++;
            }
            Assertions.assertEquals(10, received);
        }
        TimeUnit.MILLISECONDS.sleep(250); // wait clean for shutdown
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

            Map<String, Object> rpcResponse;
            try {
                rpcResponse = Json.mapper().readValue(message, Map.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Unable to populate line bean.", e);
            }
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

    private static List<Line> parseLines(String json) throws JsonProcessingException {
        Line[] lines = Json.mapper().readValue(json, Line[].class);
        return Arrays.asList(lines);
    }
}

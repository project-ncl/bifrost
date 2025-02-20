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

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.apache.commons.beanutils.BeanUtils;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.bifrost.common.scheduler.Subscriptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ServerEndpoint("/socket")
public class Socket {

    private static final String className = Socket.class.getName();
    public static final String CONNECTION_CLOSED_BY_USER = "Connection reset by peer";

    private Logger logger = LoggerFactory.getLogger(Socket.class);

    @Inject
    Subscriptions subscriptions;

    @Inject
    MethodFactory methodFactory;

    @Inject
    MeterRegistry registry;

    private Counter errCounter;
    private Counter warnCounter;

    @PostConstruct
    void initMetrics() {
        errCounter = registry.counter(className + ".error.count");
        warnCounter = registry.counter(className + ".warning.count");
    }

    private SendHandler commandResponseHandler = result -> {
        if (!result.isOK()) {
            errCounter.increment();
            logger.error("Error sending command response.", result.getException());
        }
    };

    private String LOGLINE_NOTIFICATION = "LOG";

    @OnOpen
    public void open(Session session) {
    }

    @OnClose
    public void close(Session session) {
        unsubscribeSession(session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        errCounter.increment();
        if (CONNECTION_CLOSED_BY_USER.equals(error.getMessage())) {
            logger.warn("Socket closed by user.", error);
        } else {
            logger.warn("Socket communication error.", error);
        }
        unsubscribeSession(session.getId());
    }

    private void unsubscribeSession(String sessionId) {
        subscriptions.getAll()
                .stream()
                .filter(s -> s.getClientId().equals(sessionId))
                .forEach(s -> subscriptions.unsubscribe(s));
    }

    @Timed
    @OnMessage
    public void handleMessage(String message, Session session) {
        logger.debug("Received message: " + message);
        RemoteEndpoint.Async remote = session.getAsyncRemote();

        JSONRPC2Request request;
        try {
            request = JSONRPC2Request.parse(message);
        } catch (JSONRPC2ParseException e) {
            errCounter.increment();
            String err = "Cannot parse request.";
            logger.error(err, e);
            sendErrorResult(remote, "undefined", new JSONRPC2Error(-11, err + e.getMessage(), null));
            return;
        }
        Object requestId = request.getID();

        Optional<Method<?>> maybeMethod = methodFactory.get(request.getMethod());
        if (!maybeMethod.isPresent()) {
            warnCounter.increment();
            String err = "Unsupported method " + request.getMethod();
            logger.warn(err);
            sendErrorResult(remote, requestId, new JSONRPC2Error(-12, err, null));
            return;
        }
        Method method = maybeMethod.get();
        method.setSession(session);
        logger.info("Requested method: " + method.getName());

        Object methodParameter = null;
        try {
            methodParameter = method.getParameterType().getConstructor(new Class[] {}).newInstance(new Object[] {});

            var namedParameters = request.getNamedParams();

            // Don't try to populate 'null' parameters because null Strings would result in empty Strings and Integers
            // in 0. We want to pass 'null' values to the method.
            namedParameters.entrySet().removeIf(entry -> entry.getValue() == null);

            BeanUtils.populate(methodParameter, namedParameters);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException e) {
            errCounter.increment();
            String err = "Cannot construct parameters for method: " + request.getMethod() + ".";
            logger.error(err, e);
            sendErrorResult(remote, requestId, new JSONRPC2Error(-13, err + e.getMessage(), null));
            return;
        }

        Consumer<Line> lineConsumer = line -> sendLine(remote, line, requestId, session);

        Result result = method.apply(methodParameter, lineConsumer);
        logger.debug("Method invoked, result: " + result);
        sendResult(remote, requestId, result);
    }

    private void sendErrorResult(RemoteEndpoint.Async remote, Object requestId, JSONRPC2Error error) {
        JsonbJSONRPC2Response jsonrpc2Response = new JsonbJSONRPC2Response(error, requestId);

        sendResponse(remote, jsonrpc2Response);
    }

    private void sendResult(RemoteEndpoint.Async remote, Object requestId, Result result) {
        JsonbJSONRPC2Response jsonrpc2Response = new JsonbJSONRPC2Response(result, requestId);
        sendResponse(remote, jsonrpc2Response);
    }

    private void sendResponse(RemoteEndpoint.Async remote, JsonbJSONRPC2Response jsonrpc2Response) {
        String responseString = jsonrpc2Response.toJSONString();
        remote.sendText(responseString, commandResponseHandler);
        logger.debug("Text response sent: " + responseString);
    }

    private void sendLine(RemoteEndpoint.Async remote, Line line, Object requestId, Session session) {
        logger.debug("Sending line as text message: " + line.asString());
        JsonbJSONRPC2Response jsonrpc2Response = new JsonbJSONRPC2Response(new LineResult(line), requestId);
        remote.sendText(jsonrpc2Response.toJSONString(), lineResponseHandler(session));
    }

    private SendHandler lineResponseHandler(Session session) {
        return result -> {
            if (!result.isOK()) {
                if (result.getException()
                        .getClass()
                        .getName()
                        .equals("io.netty.channel.StacklessClosedChannelException")) {
                    // client closed the websocket channel uncleanly and bifrost server wasn't aware of it. Not a big
                    // deal in the grand scheme of things
                    logger.debug("Couldn't send log line since client closed channel:", result.getException());
                } else {
                    logger.error("Error sending log line.", result.getException());
                }
                unsubscribeSession(session.getId());
            }
        };
    }

}

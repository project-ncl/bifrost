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

import java.util.HashMap;
import java.util.Map;

import org.jboss.pnc.bifrost.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class JsonbJSONRPC2Response extends JSONRPC2Response {

    private Logger logger = LoggerFactory.getLogger(JsonbJSONRPC2Response.class);

    public JsonbJSONRPC2Response(Object result, Object id) {
        super(result, id);
    }

    public JsonbJSONRPC2Response(Object id) {
        super(id);
    }

    public JsonbJSONRPC2Response(JSONRPC2Error error, Object id) {
        super(error, id);
    }

    @Override
    public String toJSONString() {
        Map<String, Object> out = new HashMap<>();
        if (getError() != null) {
            out.put("error", getError());
        } else {
            out.put("result", getResult());
        }
        out.put("id", getID());
        out.put("jsonrpc", "2.0");
        try {
            return Json.mapper().writeValueAsString(out);
        } catch (JsonProcessingException e) {
            String message = "Cannot serialize response.";
            logger.error(message, e);
            return "{'error':'" + message + "'}";
        }
    }
}

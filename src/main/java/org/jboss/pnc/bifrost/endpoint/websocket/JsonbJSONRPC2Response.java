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
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.util.HashMap;
import java.util.Map;

public class JsonbJSONRPC2Response extends JSONRPC2Response {

    private Jsonb jsonb = JsonbBuilder.create();

    public JsonbJSONRPC2Response(Object result, Object id) {
        super(result, id);
    }

    public JsonbJSONRPC2Response(Object id) {
        super(id);
    }

    public JsonbJSONRPC2Response(JSONRPC2Error error, Object id) {
        super(error, id);
    }

    protected Jsonb getJsonb() {
        return jsonb;
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
        return getJsonb().toJson(out);
    }
}

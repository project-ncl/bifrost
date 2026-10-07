/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.bifrost.kafkaconsumer;

import org.jboss.pnc.bifrost.source.db.LogLine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class LogLineDeserializeTest {

    @Test
    public void shouldDeserializeLogRecord() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        String json = "{\n" + "    \"@timestamp\": \"2022-04-27T22:15:47.536Z\",\n" + "    \"sequence\": 20291,\n"
                + "    \"loggerClassName\": \"org.slf4j.impl.Slf4jLogger\",\n"
                + "    \"loggerName\": \"org.jboss.pnc.kafka2db\",\n" + "    \"level\": \"INFO\",\n"
                + "    \"message\": \"The message\",\n" + "    \"threadName\": \"Thread-1\",\n"
                + "    \"threadId\": 26350,\n" + "    \"mdc\": { \"processContext\": \"12345\" },\n"
                + "    \"ndc\": \"\",\n" + "    \"hostName\": \"localhost\",\n"
                + "    \"processName\": \"jboss-modules.jar\",\n" + "    \"processId\": 1522\n  }";
        LogLine logLine = mapper.readValue(json, LogLine.class);
        Assertions.assertEquals("org.jboss.pnc.kafka2db", logLine.getLoggerName());
        Assertions.assertEquals(12345L, logLine.getLogEntry().getProcessContext());
    }

    @Test
    public void shouldDeserializeLogRecord2() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        String json = "{\"@timestamp\":\"2022-06-23T12:19:54.687Z\",\"sequence\":102382,\"loggerClassName\":\"org.slf4j.impl.Slf4jLogger\",\"loggerName\":\"org.jboss.pnc._userlog_.brewpush\",\"level\":\"INFO\",\"message\":\"Push started.\",\"threadName\":\"default task-58\",\"threadId\":16212,\"mdc\":{\"requestContext\":\"a6472f74-a50\",\"processContext\":\"327784835315888128\",\"userId\":\"103\",\"buildId\":\"327032778354724864\"},\"ndc\":\"\",\"hostName\":\"orch-master-342-cvv96\",\"processName\":\"jboss-modules.jar\",\"processId\":1021,\"@version\":\"1\"}";
        LogLine logLine = mapper.readValue(json, LogLine.class);
        Assertions.assertEquals(327032778354724864L, logLine.getLogEntry().getBuildId());
        Assertions.assertEquals(327784835315888128L, logLine.getLogEntry().getProcessContext());
    }

    @Test
    public void shouldDeserializeLogRecord3() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        String json = "{\"@timestamp\":\"2022-06-23T12:19:57.238Z\",\"sequence\":140,\"loggerClassName\":\"org.slf4j.impl.Slf4jLogger\",\"loggerName\":\"org.jboss.pnc.causeway.ctl.ImportControllerImpl\",\"level\":\"INFO\",\"message\":\"Importing external build ASE5VFCHXYIAA to tag fb-1.0-pnc.\",\"threadName\":\"EJB default - 1\",\"threadId\":17102,\"mdc\":{\"requestContext\":\"a6472f74-a50\",\"processContext\":\"327784835315888128\",\"userId\":\"103\",\"buildId\":\"ASE5VFCHXYIAA\"},\"ndc\":\"\",\"hostName\":\"causeway-master-477-c69z4\",\"processName\":\"jboss-modules.jar\",\"processId\":1001,\"@version\":\"1\"}";
        LogLine logLine = mapper.readValue(json, LogLine.class);
        Assertions.assertEquals(327032778354724864L, logLine.getLogEntry().getBuildId());
        Assertions.assertEquals(327784835315888128L, logLine.getLogEntry().getProcessContext());
    }
}

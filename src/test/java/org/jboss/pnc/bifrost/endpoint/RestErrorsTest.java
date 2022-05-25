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
package org.jboss.pnc.bifrost.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.api.bifrost.rest.Bifrost;
import org.jboss.pnc.bifrost.common.Json;
import org.jboss.pnc.bifrost.endpoint.provider.DataProviderMock;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@QuarkusTest
public class RestErrorsTest {

    private static Logger logger = LoggerFactory.getLogger(RestErrorsTest.class);

    private static WebTarget target;

    @Inject
    DataProviderMock dataProvider;

    /*
     * WORKAROUND: use static block instead of BeforeAll to avoid the exception below The IllegalStateException happens
     * when this test is run the first, if any other test run before it works
     * 
     * java.lang.ExceptionInInitializerError at org.jboss.pnc.bifrost.endpoint.RestTest.init(RestTest.java:45) Caused
     * by: java.lang.IllegalStateException: No configuration is available for this class loader at
     * org.jboss.pnc.bifrost.endpoint.RestTest.init(RestTest.java:45)
     */
    // @BeforeAll
    static {
        Client client = ClientBuilder.newClient();
        target = client.target("http://localhost:8081/");
    }

    @Test
    public void shouldReturnExceptionMessage() throws IOException {
        String exceptionMessage = "Test exception.";
        dataProvider.setThrowOnCall(new IOException(exceptionMessage));
        ResteasyWebTarget rtarget = (ResteasyWebTarget) target;
        Bifrost rest = rtarget.proxy(Bifrost.class);
        String receivedExceptionMessage = null;
        try {
            rest.getLines("", "", null, Direction.ASC, 10);
        } catch (WebApplicationException e) {
            ByteArrayInputStream entityStream = (ByteArrayInputStream) e.getResponse().getEntity();
            receivedExceptionMessage = Json.mapper().readValue(entityStream, String.class);
        }
        dataProvider.removeThrowOnCall();
        Assertions.assertEquals(exceptionMessage, receivedExceptionMessage);
    }
}

package org.jboss.pnc.bifrost.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.api.bifrost.rest.Bifrost;
import org.jboss.pnc.bifrost.endpoint.provider.DataProviderMock;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
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
            Jsonb jsonb = JsonbBuilder.create();
            ByteArrayInputStream entityStream = (ByteArrayInputStream) e.getResponse().getEntity();
            receivedExceptionMessage = jsonb.fromJson(entityStream, String.class);
        }
        dataProvider.removeThrowOnCall();
        Assertions.assertEquals(exceptionMessage, receivedExceptionMessage);
    }
}

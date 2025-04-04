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
package org.jboss.pnc.bifrost.source;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.http.HttpEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.endpoint.provider.DataProvider;
import org.jboss.pnc.bifrost.source.elasticsearch.ClientFactory;
import org.jboss.pnc.bifrost.source.elasticsearch.ElasticSearchConfig;
import org.jboss.pnc.bifrost.test.DebugTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@QuarkusTest
@DebugTest
public class RemoteConnectionTest {

    private static Logger logger = LoggerFactory.getLogger(RemoteConnectionTest.class);

    @Inject
    ElasticSearchConfig elasticSearchConfig;

    @Inject
    // @MainBean // do not use the mock alternative, add qualifier to the DataProvider
    DataProvider dataProvider;

    @Test
    public void connect() throws Exception {
        RestClient lowLevelRestClient = new ClientFactory(elasticSearchConfig).getConnectedClient();
        logger.info("Connected.");

        String index = elasticSearchConfig.getIndexes().split(",")[0];
        HttpEntity entity = new BasicHttpEntity();
        Response response = lowLevelRestClient
                .performRequest("GET", "/" + index + "/_search/", Collections.emptyMap(), entity);
        assert 200 == response.getStatusLine().getStatusCode();
        lowLevelRestClient.close();
    }

    @Test
    public void shouldQueryRemoteServer() throws Exception {
        BlockingQueue<Line> lines = new ArrayBlockingQueue<>(15);
        Consumer<Line> onLine = line -> {
            logger.info("line: " + line.getId() + " - " + line.getMessage());
            boolean inserted = lines.offer(line);
            assert inserted;
        };
        // dataProvider.get("", "", Optional.empty(), Direction.DESC, Optional.of(10), onLine);
        Line after = Line.newBuilder().id("log#AXMG530ewm5cr6w_UJtL").timestamp("2020-06-30T20:24:37.197Z").build();
        dataProvider.get("", "", Optional.of(after), Direction.DESC, Optional.of(15), Optional.empty(), onLine);

        List<Line> received = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Line polled = lines.poll(5, TimeUnit.SECONDS);
            if (polled == null) {
                break;
            }
            received.add(polled);
        }
        Assertions.assertEquals(15, received.size());
    }
}
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
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.common.Json;
import org.jboss.pnc.bifrost.mock.LineProducer;
import org.jboss.pnc.bifrost.source.elasticsearch.ClientFactory;
import org.jboss.pnc.bifrost.source.elasticsearch.ElasticSearch;
import org.jboss.pnc.bifrost.source.elasticsearch.ElasticSearchConfig;
import org.jboss.pnc.bifrost.test.Wait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@QuarkusTest
@Disabled
// although no need to boot the application, the logging does not work without this annotation
public class ElasticSearchEmbeddedIT {

    private static Logger logger = LoggerFactory.getLogger(ElasticSearchEmbeddedIT.class);

    private static String indexes = "test";

    private String defaultLogger = "org.jboss.pnc._userlog_";

    private static ElasticSearchConfig elasticSearchConfig = ElasticSearchConfig.newBuilder()
            .hosts("http://localhost:9200")
            .indexes(indexes)
            .build();

    private static ClientFactory clientFactory = new ClientFactory(elasticSearchConfig);

    @BeforeAll
    public static void init() throws Exception {
        RestClient lowLevelRestClient = clientFactory.getConnectedClient();

        Map<String, String> typeKeyword = Collections.singletonMap("type", "keyword");
        Map<String, String> typeDate = Collections.singletonMap("type", "date");

        Map<String, Object> fields = new HashMap<>();
        fields.put("id", typeKeyword);
        fields.put("@timestamp", typeDate);
        fields.put("sequence", typeKeyword);

        Map<String, Object> properties = Collections.singletonMap("properties", fields);
        Map<String, Object> doc = Collections.singletonMap("doc", properties);
        Map<String, Object> mappings = Collections.singletonMap("mappings", doc);

        String jsonString = Json.mapper().writeValueAsString(mappings);

        logger.info("Json create index: " + jsonString);

        HttpEntity entity = new NStringEntity(jsonString, ContentType.APPLICATION_JSON);
        lowLevelRestClient.performRequest("PUT", "/test", Collections.emptyMap(), entity);
        lowLevelRestClient.close();
    }

    @Test
    public void shouldInsertAndReadData() throws Exception {

        Line insertLine = LineProducer.getLine(1, true, "fddgfh");

        byte[] jsonLine = Json.mapper().writeValueAsBytes(insertLine);
        logger.info("json line: " + new String(jsonLine));

        RestClient restClient = clientFactory.getConnectedClient();
        RestHighLevelClient client = new RestHighLevelClient(restClient);
        IndexRequest indexRequest = new IndexRequest("test", "doc", "1");
        indexRequest.source(jsonLine, XContentType.JSON);
        IndexResponse response = client.index(indexRequest);

        logger.info("Response status: " + response.status());
        Assertions.assertEquals(RestStatus.CREATED, response.status());

        // wait to stabilize
        Thread.sleep(1000);

        ElasticSearch elasticSearchSource = new ElasticSearch(elasticSearchConfig);

        Map<String, List<String>> noFilters = Collections.emptyMap();

        AtomicReference<Line> receivedLine = new AtomicReference<>();
        Consumer<Line> onLine = line -> {
            logger.info("Found line: " + line);
            receivedLine.set(line);
        };
        elasticSearchSource.get(noFilters, noFilters, Optional.empty(), Direction.ASC, 10, onLine);

        Wait.forCondition(() -> receivedLine.get() != null, 10L, ChronoUnit.SECONDS);
        restClient.close();
    }

    @Test
    public void shouldGetLinesMatchingCtxAndLoggerPrefix() throws Exception {
        insertLine(2, "build-1", "other." + defaultLogger);
        insertLine(2, "build-1", defaultLogger);
        insertLine(5, "build-2", defaultLogger);
        insertLine(5, "build-2", defaultLogger + ".build-log");
        insertLine(4, "build 2", defaultLogger); // note the ctx
        Thread.sleep(1000);

        ElasticSearch elasticSearch = new ElasticSearch(elasticSearchConfig);

        List<Line> anyLines = new ArrayList<>();
        Consumer<Line> anyLine = (line -> {
            logger.info("Found line: " + line);
            anyLines.add(line);
        });

        Map<String, List<String>> defaultLogMatcher = Collections
                .singletonMap("loggerName.keyword", Arrays.asList(defaultLogger));
        elasticSearch.get(Collections.emptyMap(), defaultLogMatcher, Optional.empty(), Direction.ASC, 100, anyLine);
        Assertions.assertEquals(16, anyLines.size());

        Map<String, List<String>> matchFilters = new HashMap<>();
        matchFilters.put("mdc.processContext.keyword", Arrays.asList("build-2"));
        List<Line> matchingLines = new ArrayList<>();
        Consumer<Line> onLine = (line -> {
            logger.info("Found line: " + line);
            matchingLines.add(line);
        });
        elasticSearch.get(matchFilters, defaultLogMatcher, Optional.empty(), Direction.ASC, 100, onLine);
        Assertions.assertEquals(10, matchingLines.size());
        elasticSearch.close();
    }

    private void insertLine(Integer numberOfLines, String ctx, String loggerName) throws Exception {
        List<Line> lines = LineProducer.getLines(numberOfLines, ctx, loggerName);
        for (Line line : lines) {
            byte[] jsonLine = Json.mapper().writeValueAsBytes(line);
            RestClient restClient = clientFactory.getConnectedClient();
            RestHighLevelClient client = new RestHighLevelClient(restClient);
            IndexRequest indexRequest = new IndexRequest("test", "doc");
            indexRequest.source(jsonLine, XContentType.JSON);
            IndexResponse response = client.index(indexRequest);

            Assertions.assertEquals(RestStatus.CREATED, response.status());
            restClient.close();
        }
    }
}
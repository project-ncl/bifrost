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
import org.jboss.logging.Logger;
import org.jboss.pnc.bifrost.mock.LineProducer;
import org.jboss.pnc.bifrost.source.dto.Direction;
import org.jboss.pnc.bifrost.source.dto.Line;
import org.jboss.pnc.bifrost.test.ElasticsearchEmbeddedTest;
import org.jboss.pnc.bifrost.test.Wait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
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
@QuarkusTest //although no need to boot the applicaiton, the logging does not work without this annota
@ElasticsearchEmbeddedTest
public class ElasticSearchEmbeddedIT {

    private static Logger logger = Logger.getLogger(ElasticSearchEmbeddedIT.class);

    private static String indexes = "test";

    private String defaultLogger = "org.jboss.pnc._userlog_";

    private static ElasticSearchConfig elasticSearchConfig = ElasticSearchConfig.newBuilder()
        .hosts("http://localhost:9200")
        .indexes(indexes)
        .build();

    private static ClientFactory clientFactory = new ClientFactory(elasticSearchConfig);


    @BeforeAll
    public static void init() throws Exception {
        JsonbConfig config = new JsonbConfig().withFormatting(true);
        Jsonb jsonb = JsonbBuilder.create(config);

        RestClient lowLevelRestClient = clientFactory.getConnectedClient();

        Map<String, String> typeKeyword = Collections.singletonMap("type", "keyword");
        Map<String, String> typeDate = Collections.singletonMap("type", "date");

        Map<String, Object> fields = new HashMap<>();
        fields.put("id", typeKeyword);
        fields.put("@timestamp", typeDate);

        Map<String, Object> properties = Collections.singletonMap("properties", fields);
        Map<String, Object> doc = Collections.singletonMap("doc", properties);
        Map<String, Object> mappings = Collections.singletonMap("mappings", doc);

        String jsonString = jsonb.toJson(mappings);

        logger.info("Json create index: " + jsonString);

        HttpEntity entity = new NStringEntity(jsonString, ContentType.APPLICATION_JSON);
        lowLevelRestClient.performRequest("PUT", "/test", Collections.emptyMap(), entity);
        lowLevelRestClient.close();
    }

    @Test
    public void shouldInsertAndReadData() throws Exception {
        JsonbConfig config = new JsonbConfig().withFormatting(true);
        Jsonb jsonb = JsonbBuilder.create(config);

        Line insertLine = LineProducer.getLine(1, true, "fddgfh");

        byte[] jsonLine = jsonb.toJson(insertLine).getBytes();
        logger.info("json line: " + new String(jsonLine));

        RestClient restClient = clientFactory.getConnectedClient();
        RestHighLevelClient client = new RestHighLevelClient(restClient);
        IndexRequest indexRequest = new IndexRequest("test", "doc", "1");
        indexRequest.source(jsonLine, XContentType.JSON);
        IndexResponse response = client.index(indexRequest);

        logger.info("Response status: " + response.status());
        Assertions.assertEquals(RestStatus.CREATED, response.status());

        //wait to stabilize
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
    public void shouldGetLinesMatchingCtxAndLoggerPrefix()
            throws Exception {
        insertLine(2, "build-1", "other." + defaultLogger);
        insertLine(2, "build-1", defaultLogger);
        insertLine(5, "build-2", defaultLogger);
        insertLine(5, "build-2", defaultLogger + ".build-log");
        insertLine(4, "build 2", defaultLogger); //note the logger name
        Thread.sleep(1000);

        ElasticSearch elasticSearch = new ElasticSearch(elasticSearchConfig);

        List<Line> anyLines = new ArrayList<>();
        Consumer<Line> anyLine = (line -> {
            logger.info("Found line: " + line);
            anyLines.add(line);
        });

        Map<String, List<String>> defaultLogMatcher = Collections.singletonMap("loggerName.keyword", Arrays.asList(defaultLogger));
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
        Assertions.assertEquals(10  , matchingLines.size());
        elasticSearch.close();
    }

//    @Test
//    public void shouldGetLinesAfter() throws Exception {
//        insertLine(5, "should");
//        Thread.sleep(100);
//        insertLine(5, "should");
//        Thread.sleep(1000);
//
//        ElasticSearch elasticSearch = new ElasticSearch(elasticSearchConfig);
//        ResultProcessor source = new ResultProcessor(elasticSearch);
//
//        List<Line> lines = source.get("ctx:should", "logger:" + defaultLogger, Optional.empty(), Direction.ASC, 5);
//
//        Assertions.assertEquals(5, lines.size());
//        lines.forEach(System.out::println);
//
//
//        Line afterLine = lines.get(4);
//        List<Line> newLines = source.get("ctx:should", "logger:" + defaultLogger, Optional.of(afterLine), Direction.ASC, 5);
//
//        Assertions.assertEquals(5, newLines.size());
//        Assertions.assertTrue(
//                Long.parseLong(lines.get(4).getTimestamp()) < Long.parseLong(newLines.get(0).getTimestamp())
//                );
//        newLines.forEach(System.out::println);
//        elasticSearch.close();
//    }

//    @Test
//    public void shouldGetAllLines() throws Exception {
//        insertLine(20, "all");
//        Thread.sleep(1000);
//
//        ElasticSearch elasticSearch = new ElasticSearch(elasticSearchConfig);
//        ResultProcessor source = new ResultProcessor(elasticSearch);
//
////        List<Line> lines = new ArrayList<>();
////        Consumer<Line> onLine = line -> {
////            lines.add(line);
////        };
//        List<Line> lines = source.get(
//                "ctx=all",
//                "logger=" + defaultLogger,
//                Optional.empty(),
//                Direction.ASC,
//                4);
//
////        Wait.forCondition(()->lines.size() == 20, 3, ChronoUnit.SECONDS);
//
//        Assertions.assertEquals(20, lines.size());
//        lines.forEach(System.out::println);
//        elasticSearch.close();
//    }

//    @Test
//    public void shouldSubscribeToNewLines() throws JsonProcessingException, InterruptedException, TimeoutException {
//        //TODO test shouldSubscribeToNewLines
//    }

    private void insertLine(Integer numberOfLines, String ctx, String loggerName) throws Exception {
        JsonbConfig config = new JsonbConfig().withFormatting(true);
        Jsonb jsonb = JsonbBuilder.create(config);

        List<Line> lines = LineProducer.getLines(numberOfLines, ctx, loggerName);
        for (Line line : lines) {
            byte[] jsonLine = jsonb.toJson(line).getBytes();
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
package org.jboss.pnc.bifrost.source;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ElasticSearch {

    private final Logger logger = LoggerFactory.getLogger(ElasticSearch.class);

    private RestClient lowLevelRestClient;
    private RestHighLevelClient client;

    private String[] indexes;

    private ElasticSearchConfig elasticSearchConfig;

    public ElasticSearch(ElasticSearchConfig elasticSearchConfig) {
        this.elasticSearchConfig = elasticSearchConfig;
        init();
    }

    public void init() {
        try {
            lowLevelRestClient = new ClientFactory(elasticSearchConfig).getConnectedClient();
        } catch (Exception e) {
            logger.error("Cannot connect client.", e);
        }
        this.indexes = elasticSearchConfig.getIndexes().split(",");
        client = new RestHighLevelClient(lowLevelRestClient);
    }

    public void close() {
        try {
            lowLevelRestClient.close();
        } catch (IOException e) {
            logger.error("Cannot close Elastisearch client.", e);
        }
    }

    /**
     * Queries the source and call onLine in the same thread when a new line is received. Method returns when all the
     * lines are fetched.
     */
    public void get(
            Map<String, List<String>> matchFilters,
            Map<String, List<String>> prefixFilters,
            Optional<Line> searchAfter,
            Direction direction,
            int fetchSize,
            Consumer<Line> onLine) throws IOException {
        logger.debug(
                "Searching matchFilters: {}, prefixFilters: {}, searchAfter: {}, direction: {}.",
                matchFilters,
                prefixFilters,
                searchAfter,
                direction);
        QueryBuilder queryBuilder = getQueryBuilder(matchFilters, prefixFilters);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(queryBuilder)
                .size(fetchSize + 1)
                .from(0)
                .sort(new FieldSortBuilder("@timestamp").order(getSortOrder(direction)))
                .sort(new FieldSortBuilder("sequence").order(getSortOrder(direction)))
                .sort(new FieldSortBuilder("_uid").order(getSortOrder(direction)));
        if (searchAfter.isPresent()) {
            String timestamp = searchAfter.get().getTimestamp();
            TemporalAccessor accessor = DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(timestamp);
            Object[] searchAfterTimeStampId = new Object[] { Instant.from(accessor).toEpochMilli(),
                    searchAfter.get().getSequence(), searchAfter.get().getId() };
            sourceBuilder.searchAfter(searchAfterTimeStampId);
        } else {
            // TODO tailFromNow vs tailFromBeginning
            // RangeQueryBuilder timestampRange = QueryBuilders.rangeQuery("@timestamp");
            // timestampRange.from(System.currentTimeMillis() - 5000); //TODO parametrize how long back
            // queryBuilder.must(timestampRange);
        }

        logger.debug("Search query: " + sourceBuilder);

        SearchRequest searchRequest = new SearchRequest(indexes);
        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest);

        SearchHits responseHits = response.getHits();
        logger.info("Total hits: {}, limited to {}.", responseHits.getTotalHits(), fetchSize);
        int hitNum = 0;

        /**
         * loop until fetchSize or all the elements are read note that (fetchSize + 1) is used as a limit in the query
         * to check if there are more results
         */
        Iterator<SearchHit> responseHitIterator = responseHits.iterator();
        while (responseHitIterator.hasNext() && hitNum < fetchSize) {
            hitNum++;
            SearchHit hit = responseHitIterator.next();
            boolean last = !responseHitIterator.hasNext();
            Line line = getLine(hit, last);
            onLine.accept(line);
        }

        if (hitNum == 0) {
            logger.debug("There are no results.");
            onLine.accept(null);
        }
    }

    private SortOrder getSortOrder(Direction direction) {
        switch (direction) {
            case ASC:
                return SortOrder.ASC;
            case DESC:
                return SortOrder.DESC;
            default:
                throw new RuntimeException("Unsupported direction: " + direction.toString());
        }
    }

    private Line getLine(SearchHit hit, boolean last) {
        Map<String, Object> source = hit.getSource();
        logger.trace("Received line {}", source);

        // String id = source.get("_type").toString() + "#" + source.get("_id").toString();
        String id = hit.getType() + "#" + hit.getId();
        String timestamp = getString(source, "@timestamp");
        String sequence = getString(source, "sequence");
        String logger = getString(source, "loggerName");
        String message = getString(source, "message");

        Map<String, String> mdc = (Map<String, String>) source.get("mdc");

        return Line.newBuilder()
                .id(id)
                .timestamp(timestamp)
                .sequence(sequence)
                .logger(logger)
                .message(message)
                .last(last)
                .mdc(mdc)
                .build();
    }

    private String getString(Map<String, Object> source, String fieldName) {
        Object obj = source.get(fieldName);
        if (obj == null) {
            return null;
        } else {
            return obj.toString();
        }
    }

    private QueryBuilder getQueryBuilder(
            Map<String, List<String>> matchFilters,
            Map<String, List<String>> prefixFilters) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        matchFilters.forEach((field, values) -> {
            BoolQueryBuilder matchBuilder = QueryBuilders.boolQuery();
            values.forEach(value -> {
                matchBuilder.should(QueryBuilders.matchQuery(field, value));
            });
            matchBuilder.minimumShouldMatch(1);
            queryBuilder.must().add(matchBuilder);
        });

        prefixFilters.forEach((field, values) -> {
            BoolQueryBuilder prefixBuilder = QueryBuilders.boolQuery();
            values.forEach(value -> {
                prefixBuilder.should(QueryBuilders.prefixQuery(field, value));
            });
            prefixBuilder.minimumShouldMatch(1);
            queryBuilder.must().add(prefixBuilder);
        });
        return queryBuilder;
    }
}

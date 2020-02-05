package org.jboss.pnc.bifrost.endpoint.provider;

import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.api.bifrost.enums.Direction;
import org.jboss.pnc.bifrost.mock.LineProducer;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Alternative()
@Priority(1)
@ApplicationScoped
public class DataProviderMock extends DataProvider {

    Deque<Line> lines = new LinkedList<>();

//    @Inject
//    Subscriptions subscriptions;

    public DataProviderMock() {
        super();
    }

    @Override
    public void get(
            String matchFilters,
            String prefixFilters,
            Optional<Line> afterLine,
            Direction direction,
            Optional<Integer> maxLines,
            Consumer<Line> onLine) {
        LineProducer.getLines(5, "abc123").forEach(
                line -> onLine.accept(line)
        );
    }

    @Override
    protected void readFromSource(
            String matchFilters,
            String prefixFilters,
            int fetchSize,
            Optional<Line> lastResult,
            Consumer<Line> onLine) throws IOException {

        for (int i = 0; i < fetchSize; i++) {
            if (lines.isEmpty()) {
                break;
            }
            Line line = lines.pop();
            onLine.accept(line);
        }
    }

    public void addLine(Line line) {
        lines.add(line);
    }

    public void addAllLines(List<Line> lines) {
        for (Line line : lines) {
            addLine(line);
        }
    }
}
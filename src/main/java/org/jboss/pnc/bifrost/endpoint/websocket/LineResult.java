package org.jboss.pnc.bifrost.endpoint.websocket;

import lombok.NoArgsConstructor;
import org.jboss.pnc.api.bifrost.dto.Line;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@NoArgsConstructor
public class LineResult extends Result<Line> {

    public LineResult(Line line) {
        this.value = line;
    }
}

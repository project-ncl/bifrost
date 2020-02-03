package org.jboss.pnc.bifrost.endpoint.websocket;

import org.jboss.pnc.api.bifrost.dto.Line;

import javax.websocket.Session;
import java.util.function.Consumer;

/**
 *
 * @author Honza Brázdil &lt;jbrazdil@redhat.com&gt;
 * 
 * @param <T> parameter type
 */

public interface Method<T> {
    String getName();

    Class<T> getParameterType();

    void setSession(Session session);

    Result apply(T t, Consumer<Line> c);
}

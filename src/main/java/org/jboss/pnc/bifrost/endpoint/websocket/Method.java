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
package org.jboss.pnc.bifrost.endpoint.websocket;

import org.jboss.pnc.api.bifrost.dto.Line;

import jakarta.websocket.Session;
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

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

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public abstract class Result<T> {

    String type;

    T value;

    public String getType() {
        if (type != null) {
            return type;
        } else {
            return this.getClass().getCanonicalName();
        }
    }

    public void setValue(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }
}

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
package org.jboss.pnc.bifrost.source.db.converter;

import jakarta.ws.rs.BadRequestException;

import org.jboss.pnc.common.pnc.LongBase32IdConverter;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class IdConverter implements ValueConverter<Long> {

    /**
     * Converts a log identifier (optionally prefixed with <code>build-</code>) to its numeric form.
     *
     * @throws BadRequestException if the value is not a valid identifier. The message contains the raw input.
     */
    @Override
    public Long convert(String value) {
        try {
            return LongBase32IdConverter.toLong(stripPrefix(value));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid log identifier '" + value + "': " + e.getMessage(), e);
        }
    }

    private String stripPrefix(String processContext) {
        if (processContext == null) {
            return null;
        }
        if (processContext.startsWith("build-")) {
            return processContext.substring(6);
        } else {
            return processContext;
        }
    }

}

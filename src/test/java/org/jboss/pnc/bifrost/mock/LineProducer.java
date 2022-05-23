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
package org.jboss.pnc.bifrost.mock;

import org.jboss.pnc.api.bifrost.dto.Line;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class LineProducer {

    public static Line getLine(Integer lineNumber, boolean last, String ctx) {
        return getLine(lineNumber, last, ctx, "org.jboss.pnc._userlog_");
    }

    public static Line getLine(Integer lineNumber, boolean last, String ctx, String loggerName) {
        var mdc = new HashMap<String, String>();
        mdc.put("processContext", ctx);
        mdc.put("tmp", "false");
        return Line.newBuilder()
                .id(UUID.randomUUID().toString())
                .timestamp(Long.toString(System.currentTimeMillis()))
                .logger(loggerName)
                .message("Message " + lineNumber)
                .last(last)
                .mdc(mdc)
                .build();
    }

    public static List<Line> getLines(Integer numberOfLines, String ctx) {
        return getLines(numberOfLines, ctx, "org.jboss.pnc._userlog_");
    }

    public static List<Line> getLines(Integer numberOfLines, String ctx, String loggerName) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < numberOfLines; i++) {
            boolean last = i == numberOfLines - 1;
            lines.add(getLine(i, last, ctx, loggerName));
        }
        return lines;
    }
}

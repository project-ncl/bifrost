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
package org.jboss.pnc.bifrost.kafkaconsumer;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import org.jboss.pnc.bifrost.source.db.LogLevel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ConfigMapping(prefix = "kafka2db")
public interface Configuration {

    /**
     * When defined it's expected to follow stateful-set pattern "podName-n". Host name is used to set the sequence
     * generator nodeId.
     */
    @Pattern(regexp = "^.+-[0-9]+")
    String hostname();

    /**
     * If not defined all the logs are stored. If filters are defined only logs matching the filter are stored.
     */
    List<LogFilter> acceptFilters();

    /**
     * Logs that should not be processed at all
     */
    List<LogDenyFilter> denyFilters();

    /**
     * Used by ID generator to guarantee unique id across clusters. cluster-sequence is added to pod's ordinal number
     * maintained by a StatefulSet.
     */
    @Max(24)
    @WithDefault("0")
    int clusterSequence();

    /**
     * Log every N messages
     */
    @WithDefault("100")
    @WithName("log-every-n-messages")
    int logEveryNMessages();

    interface LogFilter {
        String loggerNamePrefix();

        LogLevel level();
    }

    interface LogDenyFilter {
        String loggerNamePrefix();
    }

}
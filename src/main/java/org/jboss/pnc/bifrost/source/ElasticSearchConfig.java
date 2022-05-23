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
package org.jboss.pnc.bifrost.source;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class ElasticSearchConfig {

    @ConfigProperty(name = "elasticsearch.hosts")
    String hosts;

    @ConfigProperty(name = "elasticsearch.indexes")
    String indexes;

    @ConfigProperty(name = "elasticsearch.keyStorePath")
    Optional<String> keyStorePath;

    @ConfigProperty(name = "elasticsearch.keyStorePass")
    Optional<String> keyStorePass;

    @ConfigProperty(name = "elasticsearch.keyPass")
    Optional<String> keyPass;

    public String getHosts() {
        return hosts;
    }

    public String getIndexes() {
        return indexes;
    }

    public Optional<String> getKeyStorePath() {
        return keyStorePath;
    }

    public Optional<String> getKeyStorePass() {
        return keyStorePass;
    }

    public Optional<String> getKeyPass() {
        return keyPass;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private String hosts;

        private String indexes;

        private String keyStorePath;

        private String keyStorePass;

        private String keyPass;

        private Builder() {
        }

        public Builder hosts(String hosts) {
            this.hosts = hosts;
            return this;
        }

        public Builder indexes(String indexes) {
            this.indexes = indexes;
            return this;
        }

        public Builder keyStorePath(String keyStorePath) {
            this.keyStorePath = keyStorePath;
            return this;
        }

        public Builder keyStorePass(String keyStorePass) {
            this.keyStorePass = keyStorePass;
            return this;
        }

        public Builder keyPass(String keyPass) {
            this.keyPass = keyPass;
            return this;
        }

        public ElasticSearchConfig build() {
            ElasticSearchConfig elasticSearchConfig = new ElasticSearchConfig();
            elasticSearchConfig.hosts = hosts;
            elasticSearchConfig.indexes = indexes;
            elasticSearchConfig.keyStorePath = Optional.ofNullable(keyStorePath);
            elasticSearchConfig.keyStorePass = Optional.ofNullable(keyStorePass);
            elasticSearchConfig.keyPass = Optional.ofNullable(keyPass);
            return elasticSearchConfig;
        }
    }
}

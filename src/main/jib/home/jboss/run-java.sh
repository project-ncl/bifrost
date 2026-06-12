#!/bin/bash
#
# JBoss, Home of Professional Open Source.
# Copyright 2019 Red Hat, Inc., and individual contributors
# as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


# TODO: once we move to external-secrets operator, we can in theory remove
#       that script

# NCL-7116: avoid using openshift secrets with app secrets
SECRETS_DIR="/mnt/secrets"
export QUARKUS_DATASOURCE_JDBC_URL="$(cat ${SECRETS_DIR}/${BIFROST_SECRET_NAME}-${APP_ENV}/datasource.connection)"
export QUARKUS_DATASOURCE_USERNAME="$(cat ${SECRETS_DIR}/${BIFROST_SECRET_NAME}-${APP_ENV}/datasource.user)"
export QUARKUS_DATASOURCE_PASSWORD="$(cat ${SECRETS_DIR}/${BIFROST_SECRET_NAME}-${APP_ENV}/datasource.password)"
export client_jaas_conf="$(cat ${SECRETS_DIR}/${KAFKA_SECRET_NAME}-$APP_ENV/kafka_jaas_conf)"

export QUARKUS_DATASOURCE_JDBC_URL
export QUARKUS_DATASOURCE_USERNAME
export QUARKUS_DATASOURCE_PASSWORD

java \
    -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
    -XX:MaxRAMPercentage=80.0 \
    -jar quarkus-run.jar

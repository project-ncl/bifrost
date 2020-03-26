#!/bin/sh

JAVA_OPTIONS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager \
-Delasticsearch.hosts= \
-Delasticsearch.indexes= \
-Delasticsearch.keyStorePath= \
-Delasticsearch.keyStorePass= \
-Delasticsearch.keyPass="

java ${JAVA_OPTIONS} -jar ./target/bifrost-server-0.1-SNAPSHOT-runner.jar
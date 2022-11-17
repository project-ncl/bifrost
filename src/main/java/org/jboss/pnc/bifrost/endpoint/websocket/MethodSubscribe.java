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

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.beanutils.BeanUtils;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.bifrost.common.scheduler.Subscription;
import org.jboss.pnc.bifrost.endpoint.provider.DataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.websocket.SendHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Dependent
public class MethodSubscribe extends MethodBase implements Method<SubscribeDto> {

    private static final String className = MethodSubscribe.class.getName();

    private Logger logger = LoggerFactory.getLogger(MethodSubscribe.class);

    private SendHandler responseHandler = result -> {
        if (!result.isOK()) {
            logger.error("Error sending command response.", result.getException());
        }
    };

    @Inject
    DataProvider dataProvider;

    @Inject
    MeterRegistry registry;

    private Counter errCounter;

    @PostConstruct
    void initMetrics() {
        errCounter = registry.counter(className + ".error.count");
    }

    @Override
    public String getName() {
        return "SUBSCRIBE";
    }

    @Override
    public Class<SubscribeDto> getParameterType() {
        return SubscribeDto.class;
    }

    @Timed
    @Override
    public Result apply(SubscribeDto subscribeDto, Consumer<Line> responseConsumer) {
        String matchFilters = subscribeDto.getMatchFilters();
        String prefixFilters = subscribeDto.getPrefixFilters();

        String topic = subscribeDto.getMatchFilters() + subscribeDto.getPrefixFilters();
        Subscription subscription = new Subscription(
                getSession().getId(),
                topic,
                () -> sendUnsubscribedNotification(topic));

        Consumer<Line> onLine = line -> {
            if (line != null) {
                line.setSubscriptionTopic(subscription.getTopic());
                responseConsumer.accept(line);
            }
        };

        Optional<Line> afterLine = Optional.ofNullable(subscribeDto.getAfterLine());
        dataProvider.subscribe(matchFilters, prefixFilters, afterLine, onLine, subscription, Optional.empty());

        return new SubscribeResultDto(subscription.getTopic());
    }

    private void sendUnsubscribedNotification(String topic) {
        UnSubscribedDto unSubscribedDto = new UnSubscribedDto();
        unSubscribedDto.setSubscriptionTopic(topic);

        try {
            Map<String, Object> parameterMap = (Map) BeanUtils.describe(unSubscribedDto);
            JSONRPC2Notification notification = new JSONRPC2Notification("UNSUBSCRIBED", parameterMap);
            String jsonString = notification.toJSONString();
            getSession().getAsyncRemote().sendText(jsonString, responseHandler);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            errCounter.increment();
            logger.error("Cannot prepare unsubscribed message.", e);
        }
    }

}

package org.jboss.pnc.bifrost.endpoint.websocket;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import org.apache.commons.beanutils.BeanUtils;
import org.jboss.logging.Logger;
import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.bifrost.common.scheduler.Subscription;
import org.jboss.pnc.bifrost.endpoint.provider.DataProvider;

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

    private Logger logger = Logger.getLogger(MethodSubscribe.class);

    private SendHandler responseHandler = result -> {
        if (!result.isOK()) {
            logger.error("Error sending command response.", result.getException());
        }
    };


    @Inject
    DataProvider dataProvider;

    @Override
    public String getName() {
        return "SUBSCRIBE";
    }

    @Override
    public Class<SubscribeDto> getParameterType() {
        return SubscribeDto.class;
    }

    @Override
    public Result apply(SubscribeDto subscribeDto, Consumer<Line> responseConsumer) {
        String matchFilters = subscribeDto.getMatchFilters();
        String prefixFilters = subscribeDto.getPrefixFilters();

        String topic = subscribeDto.getMatchFilters() + subscribeDto.getPrefixFilters();
        Subscription subscription = new Subscription(
                getSession().getId(),
                topic,
                () -> sendUnsubscribedNotification(topic)
        );

        Consumer<Line> onLine = line -> {
            if (line != null) {
                line.setSubscriptionTopic(subscription.getTopic());
                responseConsumer.accept(line);
            }
        };

        dataProvider.subscribe(matchFilters, prefixFilters, Optional.empty(), onLine, subscription, Optional.empty());

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
            logger.error("Cannot prepare unsubscribed message.", e);
        }
    }

}

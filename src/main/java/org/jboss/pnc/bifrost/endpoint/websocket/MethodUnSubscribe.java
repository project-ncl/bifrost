package org.jboss.pnc.bifrost.endpoint.websocket;

import org.jboss.pnc.api.bifrost.dto.Line;
import org.jboss.pnc.bifrost.common.scheduler.Subscription;
import org.jboss.pnc.bifrost.endpoint.provider.DataProvider;

import javax.inject.Inject;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class MethodUnSubscribe extends MethodBase implements Method<UnSubscribeDto> {

    @Inject
    DataProvider dataProvider;

    @Override
    public String getName() {
        return "UNSUBSCRIBE";
    }

    @Override
    public Class<UnSubscribeDto> getParameterType() {
        return UnSubscribeDto.class;
    }

    @Override
    public Result apply(UnSubscribeDto methodUnSubscribeIn, Consumer<Line> responseConsumer) {
        Subscription subscription = new Subscription(
                getSession().getId(),
                methodUnSubscribeIn.getSubscriptionTopic(),
                () -> {});
        dataProvider.unsubscribe(subscription);
        return new OkResult();
    }
}

package org.jboss.pnc.bifrost.common.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Getter
@Setter
@ApplicationScoped
public class BackOffRunnableConfig {

    @ConfigProperty(name = "bifrost.backoffrunnable.delayMillis")
    long delayMillis;

    @ConfigProperty(name = "bifrost.backoffrunnable.maxBackOffCycles")
    long maxBackOffCycles;

    @ConfigProperty(name = "bifrost.backoffrunnable.timeOutMillis")
    long timeOutMillis;

    @ConfigProperty(name = "bifrost.backoffrunnable.pollIntervalMillis")
    long pollIntervalMillis;

}

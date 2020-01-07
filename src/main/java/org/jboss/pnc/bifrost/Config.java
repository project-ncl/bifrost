package org.jboss.pnc.bifrost;

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
public class Config {

    @ConfigProperty(name = "bifrost.defaultSourceFetchSize", defaultValue = "100")
    int defaultSourceFetchSize;

    @ConfigProperty(name = "bifrost.maxSourceFetchSize", defaultValue = "1000")
    int maxSourceFetchSize;

    @ConfigProperty(name = "bifrost.sourcePollThreads", defaultValue = "4")
    int sourcePollThreads;


}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class StoredCounter {

    private Long count = 0L;

    /**
     * Called on each increment with the incremented count value.
     */
    private List<Consumer<Long>> incrementListeners = new ArrayList<>();

    public void addIncrementListener(Consumer<Long> onIncrement) {
        incrementListeners.add(onIncrement);
    }

    public void removeIncrementListener(Consumer<Long> onIncrement) {
        incrementListeners.remove(onIncrement);
    }

    public void increment() {
        count++;
        incrementListeners.forEach(onIncrement -> onIncrement.accept(count));
    }

}

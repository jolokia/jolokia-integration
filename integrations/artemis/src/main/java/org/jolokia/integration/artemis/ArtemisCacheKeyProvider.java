/*
 * Copyright 2024 Jolokia Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.integration.artemis;

import javax.management.ObjectInstance;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.container.ContainerLocator;
import org.jolokia.service.jmx.api.CacheKeyProvider;

public class ArtemisCacheKeyProvider extends CacheKeyProvider {

    private ActiveMQServer server;

    public ArtemisCacheKeyProvider(int pOrderId) {
        super(pOrderId);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);

        this.server = pJolokiaContext.getService(ContainerLocator.class).container(ActiveMQServer.class);
    }

    @Override
    public String determineKey(ObjectInstance objectInstance) {
        return null;
    }

}

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

import java.util.SortedSet;

import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.service.api.LogHandler;
import org.jolokia.server.core.service.impl.ClasspathServiceCreator;
import org.jolokia.server.core.service.impl.JolokiaServiceManagerImpl;
import org.jolokia.server.core.service.impl.StdoutLogHandler;
import org.jolokia.service.jmx.api.CacheKeyProvider;
import org.jolokia.service.jmx.handler.list.DataUpdater;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtensionLoadingTest {

    @Test
    public void createJolokiaServiceManager() {
        Configuration config = new StaticConfiguration();
        LogHandler logHandler = new StdoutLogHandler();
        JolokiaServiceManagerImpl manager = new JolokiaServiceManagerImpl(config, logHandler, null, null);
        manager.addServices(new ClasspathServiceCreator(this.getClass().getClassLoader(), "services"));

        SortedSet<CacheKeyProvider> cacheKeyProviders = manager.getServices(CacheKeyProvider.class);
        assertEquals(2, cacheKeyProviders.size());
        assertTrue(cacheKeyProviders.stream().anyMatch(p -> p.getClass().getName().equals(ArtemisCacheKeyProvider.class.getName())));

        SortedSet<DataUpdater> dataUpdaters = manager.getServices(DataUpdater.class);
        assertTrue(dataUpdaters.stream().anyMatch(p -> p.getClass().getName().equals(ArtemisDataUpdater.class.getName())));
    }

}

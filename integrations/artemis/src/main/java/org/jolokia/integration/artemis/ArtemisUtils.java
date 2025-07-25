/*
 * Copyright 2025 Jolokia Team
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

import java.lang.reflect.Field;
import java.util.Map;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerGuard;
import org.apache.activemq.artemis.core.server.management.GuardInvocationHandler;
import org.apache.activemq.artemis.core.server.management.JMXAccessControlList;

public class ArtemisUtils {

    private ArtemisUtils() {
    }

    public static String brokerDomain(GuardInvocationHandler guard, ActiveMQServer server) {
        if (server != null) {
            return server.getConfiguration().getJMXDomain();
        }

        // check default domain the hard way
        if (guard instanceof ArtemisMBeanServerGuard) {
            try {
                Field f = guard.getClass().getDeclaredField("jmxAccessControlList");
                f.setAccessible(true);
                Object v = f.get(guard);
                if (v instanceof JMXAccessControlList) {
                    f = v.getClass().getDeclaredField("domainAccess");
                    f.setAccessible(true);
                    v = f.get(v);
                    if (v instanceof Map && !((Map<?, ?>) v).isEmpty()) {
                        // let's take first configured domain
                        return (String) ((Map<?, ?>) v).keySet().iterator().next();
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
        }

        // fallback to default
        return ActiveMQDefaultConfiguration.getDefaultJmxDomain();
    }

}

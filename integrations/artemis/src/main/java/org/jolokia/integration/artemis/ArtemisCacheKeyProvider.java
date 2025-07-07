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

import java.util.Hashtable;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.HawtioSecurityControl;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.container.ContainerLocator;
import org.jolokia.service.jmx.api.CacheKeyProvider;

public class ArtemisCacheKeyProvider extends CacheKeyProvider {

    private ActiveMQServer server;
    private HawtioSecurityControl control;
    private String brokerDomain;

    public ArtemisCacheKeyProvider(int pOrderId) {
        super(pOrderId);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);

        server = pJolokiaContext.getService(ContainerLocator.class).container(ActiveMQServer.class);
        if (server != null) {
            brokerDomain = server.getConfiguration().getJMXDomain();
            control = (HawtioSecurityControl) server.getManagementService().getResource(ResourceNames.MANAGEMENT_SECURITY);
        }
    }

    @Override
    public String determineKey(ObjectInstance objectInstance) {
        ObjectName oName = objectInstance.getObjectName();

        // https://activemq.apache.org/components/artemis/documentation/latest/management.html
        //
        // org.apache.activemq.artemis.api.core.management.ObjectNameBuilder contains patterns for building ObjectNames for different kind of MBeans (_controls_).
        //
        // Address:            <domain>:broker=<name>,component=addresses,address=<address>
        // Queue:              <domain>:broker=<name>,component=addresses,address=<address>,subcomponent=queues,routing-type=<routing>,queue=<name>
        // Divert:             <domain>:broker=<name>,component=addresses,address=<address>,subcomponent=diverts,divert=<name>
        // Server:             <domain>:broker=<name>
        // Acceptor:           <domain>:broker=<name>,component=acceptors,name=<name>
        // Broadcast group:    <domain>:broker=<name>,component=broadcast-groups,name=<name>
        // Bridge:             <domain>:broker=<name>,component=bridges,name=<name>
        // Cluster connection: <domain>:broker=<name>,component=cluster-connnections,name=<name>
        // Connection router:  <domain>:broker=<name>,component=connection-routers,name=<name>
        // Security:           hawtio:type=security,area=jmx,name=ArtemisJMXSecurity

        if (brokerDomain.equals(oName.getDomain())) {
            Hashtable<String, String> keys = oName.getKeyPropertyList();
            if ("addresses".equals(keys.get("component"))) {
                if ("queues".equals(keys.get("subcomponent"))) {
                    // a queue - org.apache.activemq.artemis.api.core.management.QueueControl
                    return "artemis.queue";
                } else {
                    // an address - org.apache.activemq.artemis.api.core.management.AddressControl
                    return "artemis.address";
                }
            } else if ("acceptors".equals(keys.get("component"))) {
                // org.apache.activemq.artemis.api.core.management.AcceptorControl
                return "artemis.acceptor";
            }
        }
        return null;
    }

}

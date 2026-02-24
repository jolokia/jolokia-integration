/*
 * Copyright 2026 Jolokia Team
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.management.ObjectName;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ArtemisBrokerConfigurationTest {

    @Test
    public void readingBrokerXMLConfig() throws Exception {
        InputStream is = getClass().getResourceAsStream("/etc/broker.xml");
        assertNotNull(is);
        Configuration config = new FileConfigurationParser().parseMainConfig(is);
        ArtemisCacheKeyProvider provider = new ArtemisCacheKeyProvider(100);
        ArtemisCacheKeyProvider.ArtemisBrokerConfiguration configuration = new ArtemisCacheKeyProvider.ArtemisBrokerConfiguration(config);
        is.close();

        // <domain>:broker=<name>,component=addresses,address=<address>,subcomponent=queues,routing-type=<routing>,queue=<name>
        //
        // wildcardMatches: java.util.Map  = {java.util.TreeMap@2826}  size = 4
        //  {@2835} "mops.queue.q.m.prod-01.*"
        //  {@2837} "mops.queue.q.m.#"
        //  {@2839} "mops.queue.q.#"
        //  {@2841} "#"

        String brokerDomain = "org.apache.activemq.artemis";
        Base64.Decoder decoder = Base64.getDecoder();
        String sk = configuration.getSubkey(new ObjectName("org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,address=a1,subcomponent=queues,routing-type=X,queue=\"some-queue\""), brokerDomain);
        assertEquals("#", new String(decoder.decode(sk.substring(1)), StandardCharsets.UTF_8));
        sk = configuration.getSubkey(new ObjectName("org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,address=a1,subcomponent=queues,routing-type=X,queue=\"q.a.b\""), brokerDomain);
        assertEquals("mops.queue.q.#", new String(decoder.decode(sk.substring(1)), StandardCharsets.UTF_8));
        sk = configuration.getSubkey(new ObjectName("org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,address=a1,subcomponent=queues,routing-type=X,queue=\"q.m.prod-01\""), brokerDomain);
        assertEquals("mops.queue.q.m.#", new String(decoder.decode(sk.substring(1)), StandardCharsets.UTF_8));
        sk = configuration.getSubkey(new ObjectName("org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,address=a1,subcomponent=queues,routing-type=X,queue=\"q.m.prod-01.x\""), brokerDomain);
        assertEquals("mops.queue.q.m.prod-01.*", new String(decoder.decode(sk.substring(1)), StandardCharsets.UTF_8));
        sk = configuration.getSubkey(new ObjectName("org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,address=a1,subcomponent=queues,routing-type=X,queue=\"q.m.prod-01.x.y\""), brokerDomain);
        assertEquals("mops.queue.q.m.#", new String(decoder.decode(sk.substring(1)), StandardCharsets.UTF_8));
        sk = configuration.getSubkey(new ObjectName("org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,address=a1,subcomponent=queues,routing-type=X,queue=\"q.m.prod-02.x\""), brokerDomain);
        assertEquals("mops.queue.q.m.#", new String(decoder.decode(sk.substring(1)), StandardCharsets.UTF_8));
        sk = configuration.getSubkey(new ObjectName("org.apache.activemq.artemis:broker=\"0.0.0.0\",component=addresses,address=a1,subcomponent=queues,routing-type=X,queue=\"q.n.prod-02.x\""), brokerDomain);
        assertEquals("mops.queue.q.#", new String(decoder.decode(sk.substring(1)), StandardCharsets.UTF_8));
    }

}

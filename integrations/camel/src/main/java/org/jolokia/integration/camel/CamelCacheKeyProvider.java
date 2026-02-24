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
package org.jolokia.integration.camel;

import java.util.Hashtable;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.service.jmx.api.CacheKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelCacheKeyProvider extends CacheKeyProvider {

    public static final Logger LOG = LoggerFactory.getLogger(CamelCacheKeyProvider.class);

    // see org.apache.camel.management.DefaultManagementObjectNameStrategy
    public static final String TYPE_CONTEXT = "context";
    public static final String TYPE_HEALTH = "health";
    public static final String TYPE_ENDPOINT = "endpoints";
    public static final String TYPE_DATAFORMAT = "dataformats";
    public static final String TYPE_PROCESSOR = "processors";
    public static final String TYPE_CONSUMER = "consumers";
    public static final String TYPE_PRODUCER = "producers";
    public static final String TYPE_ROUTE = "routes";
    public static final String TYPE_ROUTE_GROUP = "routegroups";
    public static final String TYPE_COMPONENT = "components";
    public static final String TYPE_STEP = "steps";
    public static final String TYPE_TRACER = "tracer";
    public static final String TYPE_EVENT_NOTIFIER = "eventnotifiers";
    public static final String TYPE_THREAD_POOL = "threadpools";
    public static final String TYPE_SERVICE = "services";
    public static final String TYPE_HA = "clusterservices";

    public CamelCacheKeyProvider(int pOrderId) {
        super(pOrderId);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);
    }

    @Override
    public String determineKey(ObjectInstance objectInstance) {
        ObjectName oName = objectInstance.getObjectName();

        // https://www.javadoc.io/doc/org.apache.camel/camel-management/4.17.0/org/apache/camel/management/DefaultManagementObjectNameStrategy.html
        // org.apache.camel.management.DefaultManagementObjectNameStrategy provides methods for producing object names
        // for various management objects:
        //
        // org.apache.camel:context=myCamel,type=clusterservices,name=xxx
        // org.apache.camel:context=myCamel,type=components,name=xxx
        // org.apache.camel:context=myCamel,type=consumers,name=xxx
        // org.apache.camel:context=myCamel,type=context,name=xxx
        // org.apache.camel:context=myCamel,type=dataformats,name=xxx
        // org.apache.camel:context=myCamel,type=endpoints,name=xxx
        // org.apache.camel:context=myCamel,type=eventnotifiers,name=xxx
        // org.apache.camel:context=myCamel,type=health,name=xxx
        // org.apache.camel:context=myCamel,type=processors,name=xxx
        // org.apache.camel:context=myCamel,type=producers,name=xxx
        // org.apache.camel:context=myCamel,type=routegroups,name=xxx
        // org.apache.camel:context=myCamel,type=routes,name=xxx
        // org.apache.camel:context=myCamel,type=services,name=xxx
        // org.apache.camel:context=myCamel,type=steps,name=xxx
        // org.apache.camel:context=myCamel,type=threadpools,name=xxx
        // org.apache.camel:context=myCamel,type=tracer,name=xxx
        //
        // In a very simple route (https://github.com/hawtio/hawtio/blob/hawtio-4.6.2/tests/springboot/src/main/java/io/hawt/tests/spring/boot/SampleCamelRouter.java), I have:
        //  - 6 components
        //  - 6 consumers
        //  - 1 context
        //  - 12 endpoints
        //  - 1 health
        //  - 10 processors
        //  - 8 producers
        //  - 2 route groups
        //  - 6 routes
        //  - 20 services
        //  - 1 threadpool
        //  - 2 tracers
        //
        // In a real application from the past, I have:
        //  - 102 components
        //  - 511 consumers
        //  - 12 context
        //  - 818 endpoints
        //  - 12 errorhandlers (could be Camel 2)
        //  - 24 eventnotifiers
        //  - 3600 processors
        //  - 1764 producers
        //  - 511 routes
        //  - 548 services
        //  - 66 threadpools
        //  - 24 tracers

        if (oName.getDomain().equals("org.apache.camel")) {
            Hashtable<String, String> keys = oName.getKeyPropertyList();
            String type = keys.get("type");
            return switch (type) {
                case TYPE_CONTEXT -> "camel:context";
                case TYPE_HEALTH -> "camel:health";
                case TYPE_ENDPOINT -> "camel:endpoint";
                case TYPE_DATAFORMAT -> "camel:dataformat";
                case TYPE_PROCESSOR -> "camel:processor";
                case TYPE_CONSUMER -> "camel:consumer";
                case TYPE_PRODUCER -> "camel:producer";
                case TYPE_ROUTE -> "camel:route";
                case TYPE_ROUTE_GROUP -> "camel:routegroup";
                case TYPE_COMPONENT -> "camel:component";
                case TYPE_STEP -> "camel:step";
                case TYPE_TRACER -> "camel:tracer";
                case TYPE_EVENT_NOTIFIER -> "camel:eventnotifier";
                case TYPE_THREAD_POOL -> "camel:threadpool";
                case TYPE_SERVICE -> "camel:service";
                case TYPE_HA -> "camel:ha";
                default -> null;
            };
        }

        return null;
    }

}

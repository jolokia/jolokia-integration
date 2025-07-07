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

import java.util.Deque;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.management.MBeanInfo;
import javax.management.ObjectName;

import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.HawtioSecurityControl;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.container.ContainerLocator;
import org.jolokia.service.jmx.handler.list.DataUpdater;

/**
 * A {@link DataUpdater} for Artemis which immediately adds RBAC information, so Hawtio doesn't have to prepare
 * huge and time consuming query (see {@code processRBAC()} in Hawtio's {@code plugins/rbac/tree-processor.ts}).
 */
public class ArtemisDataUpdater extends DataUpdater {

    private ActiveMQServer server;
    private HawtioSecurityControl control;
    private String brokerDomain;

    public ArtemisDataUpdater(int pOrderId) {
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
    public String getKey() {
        // not used, because we override entire update() method
        return null;
    }

    @Override
    public void update(Map<String, Object> pMap, ObjectName pObjectName, MBeanInfo pMBeanInfo, Deque<String> pPathStack) {
        verifyThatPathIsEmpty(pPathStack);

        if (brokerDomain.equals(pObjectName.getDomain())) {
            try {
                pMap.put("canInvoke", control.canInvoke(pObjectName.getCanonicalName()));
            } catch (Exception e) {
                pMap.put("canInvoke", false);
            }
            grantAccess(pObjectName, pMap, (mbean, signature) -> {
                try {
                    return control.canInvoke(pObjectName.getCanonicalName(), signature);
                } catch (Exception e) {
                    return false;
                }
            });
        } else {
            // just mark that user can access the MBean ...
            pMap.put("canInvoke", true);
            // ... and can invoke all operations
            grantAccess(pObjectName, pMap, (mbean, signature) -> true);
        }
    }

    private void grantAccess(ObjectName pObjectName, Map<String, Object> pMap, BiFunction<ObjectName, String, Boolean> canInvoke) {
        if (pMap.containsKey("op")) {
            // "op" is a map, where the key is operation name and there are two kinds of values:
            //  - an object with keys: args, ret, desc
            //  - an array of the above objects in case of overriden methods
            JSONObject operations = (JSONObject) pMap.get("op");
            if (operations != null) {
                JSONObject opByString = new JSONObject();
                pMap.put("opByString", opByString);
                for (Map.Entry<String, Object> entry : operations.entrySet()) {
                    String name = entry.getKey();
                    Object operation = entry.getValue();
                    if (operation instanceof JSONObject) {
                        // single operation
                        JSONObject op = (JSONObject) operation;
                        configureOperation(pObjectName, opByString, name, op, canInvoke);
                    } else if (operation instanceof JSONArray) {
                        // overriden operations
                        JSONArray ops = (JSONArray) operation;
                        for (Object o : ops) {
                            JSONObject op = (JSONObject) o;
                            configureOperation(pObjectName, opByString, name, op, canInvoke);
                        }
                    }
                }
            }
        }
    }

    private void configureOperation(ObjectName pObjectName, JSONObject opByString, String name, JSONObject op, BiFunction<ObjectName, String, Boolean> canInvoke) {
        String transformedName = transformName(name, op.get("args"));
        Boolean allowed = canInvoke.apply(pObjectName, transformedName);
        op.put("canInvoke", allowed);
        // io.hawt.osgi.jmx.RBACDecorator#prepareKarafRbacInvocations() in 2.x:
        // > ! no need to copy relevant map for "op['opname']" - hawtio uses only 'canInvoke' property
        opByString.put(transformedName, Map.of("canInvoke", allowed));
    }

    private String transformName(String name, Object args) {
        // see operationToString() in packages/hawtio/src/util/jolokia.ts of @hawtio/react
        // see io.hawt.osgi.jmx.RBACDecorator#argsToString() in hawtio 2.x
        if (!(args instanceof JSONArray)) {
            return name + "()";
        }
        JSONArray argArray = (JSONArray) args;
        return String.format("%s(%s)", name, argArray.stream().map(a -> (String) ((JSONObject) a).get("type")).collect(Collectors.joining(",")));
    }

}

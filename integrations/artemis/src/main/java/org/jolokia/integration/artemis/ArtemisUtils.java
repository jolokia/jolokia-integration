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

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerGuard;
import org.apache.activemq.artemis.core.server.management.GuardInvocationHandler;
import org.apache.activemq.artemis.core.server.management.JMXAccessControlList;

public class ArtemisUtils {

    // MBean operations of org.apache.activemq.artemis.core.management.impl.AddressControlImpl
    static Set<String> addressOperations = Set.of(
            "block",
            "clearDuplicateIdCache",
            "filter",
            "password",
            "pause",
            "persist",
            "purge",
            "resume",
            "schedulePageCleanup",
            "unblock"
    );

    // Java methods for MBean attributes of org.apache.activemq.artemis.core.management.impl.AddressControlImpl
    static Set<String> addressAttributes = Set.of(
            "getAddressLimitPercent",
            "getAddressSize",
            "getAddress",
            "getAllQueueNames",
            "getBindingNames",
            "getCurrentDuplicateIdCacheSize",
            "getId",
            "getMaxPageReadBytes",
            "getMaxPageReadMessages",
            "getMessageCount",
            "getNumberOfBytesPerPage",
            "getNumberOfMessages",
            "getNumberOfPages",
            "getPrefetchPageBytes",
            "getPrefetchPageMessages",
            "getQueueCount",
            "getQueueNames",
            "getRemoteQueueNames",
            "getRolesAsJSON",
            "getRoles",
            "getRoutedMessageCount",
            "getRoutingTypesAsJSON",
            "getRoutingTypes",
            "getUnRoutedMessageCount",
            "isAutoCreated",
            "isBlockedViaManagement",
            "isInternal",
            "isPaging",
            "isPaused",
            "isRetroactiveResource",
            "isTemporary"
    );

    // MBean operations of org.apache.activemq.artemis.core.management.impl.QueueControlImpl
    static Set<String> queueOperations = Set.of(
            "browse",
            "countMessages",
            "disable",
            "enable",
            "filter",
            "flushExecutor",
            "groupByProperty",
            "groupID",
            "listConsumersAsJSON",
            "listDeliveringMessages",
            "listDeliveringMessagesAsJSON",
            "listGroupsAsJSON",
            "listMessageCounter",
            "listMessageCounterAsHTML",
            "listMessageCounterHistory",
            "listMessageCounterHistoryAsHTML",
            "listScheduledMessages",
            "listScheduledMessagesAsJSON",
            "messageCount",
            "messageID",
            "newPriority",
            "otherQueueName",
            "pageSize",
            "password",
            "pause",
            "peekFirstMessageAsJSON",
            "peekFirstScheduledMessageAsJSON",
            "persist",
            "rejectDuplicates",
            "removeAllMessages",
            "resetAllGroups",
            "resetMessageCounter",
            "resetMessagesAcknowledged",
            "resetMessagesAdded",
            "resetMessagesExpired",
            "resetMessagesKilled",
            "resume",
            "retryMessages",
            "targetQueue"
    );

    // Java methods for MBean attributes of org.apache.activemq.artemis.core.management.impl.QueueControlImpl
    static Set<String> queueAttributes = Set.of(
            "getAcknowledgeAttempts",
            "getAddress",
            "getConsumerCount",
            "getConsumersBeforeDispatch",
            "getDeadLetterAddress",
            "getDelayBeforeDispatch",
            "getDeliveringCount",
            "getDeliveringSize",
            "getDurableDeliveringCount",
            "getDurableDeliveringSize",
            "getDurableMessageCount",
            "getDurablePersistentSize",
            "getDurableScheduledCount",
            "getDurableScheduledSize",
            "getExpiryAddress",
            "getFilter",
            "getFirstMessageAge",
            "getFirstMessageAsJSON",
            "getFirstMessageTimestamp",
            "getGroupBuckets",
            "getGroupCount",
            "getGroupFirstKey",
            "getID",
            "getLastValueKey",
            "getMaxConsumers",
            "getMessageCount",
            "getMessagesAcknowledged",
            "getMessagesAdded",
            "getMessagesExpired",
            "getMessagesKilled",
            "getName",
            "getPersistentSize",
            "getPreparedTransactionMessageCount",
            "getRingSize",
            "getRoutingType",
            "getScheduledCount",
            "getScheduledSize",
            "getUser",
            "isAutoDelete",
            "isConfigurationManaged",
            "isDurable",
            "isEnabled",
            "isExclusive",
            "isGroupRebalancePauseDispatch",
            "isGroupRebalance",
            "isInternalQueue",
            "isLastValue",
            "isPaused",
            "isPersistedPause",
            "isPurgeOnNoConsumers",
            "isRetroactiveResource",
            "isTemporary"
    );

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

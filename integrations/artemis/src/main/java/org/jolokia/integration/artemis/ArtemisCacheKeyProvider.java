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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.cli.commands.tools.config.ExportProperties;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.GuardInvocationHandler;
import org.apache.activemq.artemis.core.server.reload.ReloadCallback;
import org.apache.activemq.artemis.core.settings.impl.Match;
import org.apache.activemq.artemis.dto.AuthorisationDTO;
import org.apache.activemq.artemis.dto.ManagementContextDTO;
import org.apache.activemq.artemis.dto.MatchDTO;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.container.ContainerLocator;
import org.jolokia.service.jmx.api.CacheKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtemisCacheKeyProvider extends CacheKeyProvider {

    public static final Logger LOG = LoggerFactory.getLogger(ArtemisCacheKeyProvider.class);

    private String brokerDomain;
    private ActiveMQServer server;

    // ---- RBAC data from etc/management.xml
    //      see org.apache.activemq.artemis.core.server.management.JMXAccessControlList

    private static final String WILDCARD = "*";
    private final Map<String, TreeMap<String, Pattern>> domainAccess = new HashMap<>();

    // see org.apache.activemq.artemis.core.server.management.JMXAccessControlList.keyComparator
    private final Comparator<String> keyComparator = (key1, key2) -> {
        boolean key1ContainsWildCard = key1.contains(WILDCARD);
        boolean key2ContainsWildcard = key2.contains(WILDCARD);
        if (key1ContainsWildCard && !key2ContainsWildcard) {
            return +1;
        } else if (!key1ContainsWildCard && key2ContainsWildcard) {
            return -1;
        } else if (key1.length() == key2.length()) {
            return key1.compareTo(key2);
        }

        return key2.length() - key1.length();
    };

    // ---- RBAC data from etc/broker.xml
    //      see org.apache.activemq.artemis.core.security.impl.SecurityStoreImpl

    private final AtomicReference<ArtemisBrokerConfiguration> brokerRBACConfig = new AtomicReference<>();

    public ArtemisCacheKeyProvider(int pOrderId) {
        super(pOrderId);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);

        ContainerLocator locator = pJolokiaContext.getService(ContainerLocator.class);
        if (locator != null) {
            // GuardInvocationHandler is from:
            //  - management.xml: org.apache.activemq.artemis.core.server.management.ArtemisMBeanServerBuilder.guard
            //  - broker.xml: org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler itself
            GuardInvocationHandler guard = locator.locate(GuardInvocationHandler.class);
            server = locator.locate(ActiveMQServer.class);
            brokerDomain = ArtemisUtils.brokerDomain(guard, server);

            // we have to read the source information for the roles depending on the "mode"
            //  - "old" (management.xml) - read once at the startup
            //  - "new" (broker.xml) - here the problem is that it may be changed at runtime and Artemis reloads it

            ArtemisManagementConfigurationAccess action = new ArtemisManagementConfigurationAccess();
            // see org.apache.activemq.artemis.cli.commands.Configurable.getManagementConfiguration()
            File xmlFile = new File(action.getBrokerEtc(), "management.xml");
            if (xmlFile.exists()) {
                LOG.debug("Reading RBAC information from {}", xmlFile.getAbsolutePath());
                try {
                    ManagementContextDTO managementDTO = action.getManagementDTO();
                    AuthorisationDTO authorisation = managementDTO.getAuthorisation();
                    // see org.apache.activemq.artemis.cli.factory.jmx.ManagementFactory.create()

                    // we can ignore "default access"
//                    List<AccessDTO> accessList = authorisation.getDefaultAccess().getAccess();
                    // for example (etc/management.xml):
                    // <default-access>
                    //   <!-- The "default-access" settings apply to every MBean not explicitly configured in the
                    //   "allowlist" or "role-access" sections -->
                    //   <!-- allow read-only access by default -->
                    //   <access method="list*" roles="amq"/>
                    //   ...
                    // </default-access>

                    // but we need "domain access" to determine better key for queues / addresses - we don't
                    // use the actual roles assigned, we only want to know what's the "additional key" for
                    // an ObjectName if there's a dedicated section for it, for example:
                    // <match domain="org.apache.activemq.artemis" key="address=com.example.test.*">
                    //   <access method="*" roles="junior"/>
                    // </match>
                    // <match domain="org.apache.activemq.artemis" key="address=com.example.prod.*">
                    //   <access method="*" roles="senior"/>
                    // </match>
                    // <match domain="org.apache.activemq.artemis" key="queue=queue.test.*">
                    //   ...
                    // </match>
                    // <match domain="org.apache.activemq.artemis" key="queue=queue.prod.*">
                    //   ...
                    // </match>
                    if (authorisation != null) {
                        // can be null if RBAC is defined in etc/broker.xml and the <authorisation> section
                        // is removed according to the docs
                        List<MatchDTO> matches = authorisation.getRoleAccess().getMatch();
                        for (MatchDTO match : matches) {
                            // see org.apache.activemq.artemis.core.server.management.JMXAccessControlList.Access)
                            String key = normalize(match.getKey());
                            Pattern keyPattern = Pattern.compile(key.replace(WILDCARD, ".*"));
                            TreeMap<String, Pattern> rules = domainAccess.computeIfAbsent(match.getDomain(), k -> new TreeMap<>(keyComparator));
                            rules.put(key, keyPattern);
                        }
                    }
                } catch (Exception e) {
                    LOG.info("Can't read management.xml definition. RBAC markers may not be applied correctly: {}", e.getMessage());
                }
            }

            if (server != null) {
                ReloadCallback callback = xmlConfigUri -> {
                    if (xmlConfigUri != null) {
                        Configuration config = new FileConfigurationParser().parseMainConfig(xmlConfigUri.openStream());
                        ArtemisCacheKeyProvider.this.brokerRBACConfig.set(new ArtemisBrokerConfiguration(config));
                    }
                };
                try {
                    callback.reload(server.getConfiguration().getConfigurationUrl());
                } catch (Exception e) {
                    LOG.warn("Can't reload configuration from {}", xmlFile.getAbsolutePath(), e);
                }
                // prepare for future configuration changes
                server.getReloadManager().addCallback(server.getConfiguration().getConfigurationUrl(), callback);
            }
        }
    }

    private String normalize(String v) {
        // see org.apache.activemq.artemis.core.server.management.JMXAccessControlList.normalizeKey()
        return v == null ? "" : v.endsWith("\"") ? v.replaceAll("\"", "") : v;
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
        // Cluster connection: <domain>:broker=<name>,component=cluster-connections,name=<name>
        // Connection router:  <domain>:broker=<name>,component=connection-routers,name=<name>
        // Security:           hawtio:type=security,area=jmx,name=ArtemisJMXSecurity

        if (oName.getDomain().equals(brokerDomain)) {
            Hashtable<String, String> keys = oName.getKeyPropertyList();
            // see org.apache.activemq.artemis.core.server.management.JMXAccessControlList.getRolesForObject()
            String subkey = null;

            if (server == null) {
                // etc/management.xml
                TreeMap<String, Pattern> map = domainAccess.get(brokerDomain);
                if (map != null) {
                    for (Map.Entry<String, String> e : keys.entrySet()) {
                        String key = normalize(e.getKey() + "=" + e.getValue());
                        for (Map.Entry<String, Pattern> e2 : map.entrySet()) {
                            if (e2.getValue().matcher(key).matches()) {
                                // we have a subkey
                                subkey = ":" + System.identityHashCode(e2.getKey());
                                break;
                            }
                        }
                        if (subkey != null) {
                            break;
                        }
                    }
                }
            } else {
                // etc/broker.xml
                ArtemisBrokerConfiguration configuration = this.brokerRBACConfig.get();
                subkey = configuration == null ? null : configuration.getSubkey(oName, brokerDomain);
            }

            if ("addresses".equals(keys.get("component"))) {
                if ("queues".equals(keys.get("subcomponent"))) {
                    // a queue - org.apache.activemq.artemis.api.core.management.QueueControl
                    return "artemis.queue" + (subkey == null ? "" : subkey);
                } else {
                    // an address - org.apache.activemq.artemis.api.core.management.AddressControl
                    return "artemis.address" + (subkey == null ? "" : subkey);
                }
            } else if ("acceptors".equals(keys.get("component"))) {
                // org.apache.activemq.artemis.api.core.management.AcceptorControl
                return "artemis.acceptor" + (subkey == null ? "" : subkey);
            }
        }
        return null;
    }

    private static class ArtemisManagementConfigurationAccess extends ExportProperties {
        @Override
        public ManagementContextDTO getManagementDTO() throws Exception {
            return super.getManagementDTO();
        }
    }

    static class ArtemisBrokerConfiguration {
        private final Configuration configuration;

        // see org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository
        private final Map<String, Match<String>> exactMatches = new HashMap<>();
//        private final Set<String> literalMatches = new HashSet<>();
        private final Map<String, Match<String>> wildcardMatches;

        private final String prefix;
        private final SimpleString simplePrefix;

        public ArtemisBrokerConfiguration(Configuration configuration) {
            this.configuration = configuration;

            this.prefix = configuration.getManagementRbacPrefix();
            this.simplePrefix = SimpleString.of(prefix);
            WildcardConfiguration wildcardConfig = configuration.getWildcardConfiguration();

            // we need to turn the rules from <security-settings> into a mapping for address/queue/acceptor sublists
            this.wildcardMatches = new TreeMap<>(new MatchComparator(wildcardConfig));

            Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
            configuration.getSecurityRoles().keySet().forEach(k -> {
                String pattern = stripOperation(k, prefix, wildcardConfig.getDelimiter());
                // I'll ignore these for now:
                //  - org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository.MatchModifier.modify()
                //  - org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository.checkLiteral
                // org.apache.activemq.artemis.core.config.impl.ConfigurationImpl.securitySettings is not
                // ordered, so we can't have a counter as the subkeys. We need something predictable
                String sk = "#" + encoder.encodeToString(k.getBytes(StandardCharsets.UTF_8));
                if (wildcardConfig.isWild(k)) {
                    wildcardMatches.put(k, new Match<>(k, sk, wildcardConfig));
                } else {
                    exactMatches.put(k, new Match<>(k, sk, wildcardConfig));
                }
            });
        }

        /**
         * Return a subkey for caching purposes (if exists) for specific {@link ObjectName} if this is
         * <em>different</em> queue/address than a standard one.
         *
         * @param objectName
         * @param brokerDomain
         * @return
         */
        public String getSubkey(ObjectName objectName, String brokerDomain) {
            if (objectName == null) {
                return null;
            }
            // see org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository.getMatches()
            // first I need an "address" in the form of "mops.[queue|address].[queue|address name]
            SimpleString address = addressFrom(brokerDomain, simplePrefix, objectName);

            // see org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository.getMatches()
            // now I need to find a dedicated configuration
            Match<String> exactMatch = exactMatches.get(address.toString());
            if (exactMatch != null) {
                // will never be "#" or "mops.#"
                return exactMatch.getValue();
            }
            // wildcard - ordered properly according to Artemis rules
            for (Map.Entry<String, Match<String>> e : wildcardMatches.entrySet()) {
                // org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository.merge() deals
                // with org.apache.activemq.artemis.core.settings.Mergeable, but it's not for us (queues and
                // addresses MBeans)
                if (e.getValue().getPattern().matcher(address.toString()).matches()) {
                    return e.getValue().getValue();
                }
            }
            // no match - no special queue/address configuration
            return null;
        }

        /**
         * See {@code org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler#addressFrom()}
         * @param brokerDomain
         * @param rbacPrefix
         * @param objectName
         * @return
         */
        SimpleString addressFrom(String brokerDomain, SimpleString rbacPrefix, ObjectName objectName) {
            String name = removeQuotes(objectName.getKeyProperty("name"));
            String component = removeQuotes(objectName.getKeyProperty("component"));
            String type = null;
            SimpleString rbacAddress = rbacPrefix;

            if (brokerDomain.equals(objectName.getDomain())) {
                if (component != null) {
                    if ("addresses".equals(component)) {
                        component = "address";

                        final String subComponent = objectName.getKeyProperty("subcomponent");
                        if ("diverts".equals(subComponent)) {
                            component = "divert";
                        } else if ("queues".equals(subComponent)) {
                            component = "queue";
                        }
                        name = removeQuotes(objectName.getKeyProperty(component));
                    }
                } else {
                    // broker component, server control - identified by attribute with no component
                    final String brokerName = removeQuotes(objectName.getKeyProperty("broker"));
                    if (brokerName != null) {
                        component = "broker";
                    }
                }
            } else {
                // non artemis broker domain, prefix with domain
                rbacAddress = rbacAddress.concat('.').concat(objectName.getDomain());
                type = removeQuotes(objectName.getKeyProperty("type"));
            }

            if (type != null) {
                rbacAddress = rbacAddress.concat('.').concat(type);
            }
            if (component != null) {
                rbacAddress = rbacAddress.concat('.').concat(component);
            }
            if (name != null) {
                rbacAddress = rbacAddress.concat('.').concat(name);
            }

            return rbacAddress;
        }

        /**
         * See {@code org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler#removeQuotes()}
         * @param key
         * @return
         */
        String removeQuotes(String key) {
            if (key != null && key.endsWith("\"")) {
                return key.replace("\"", "");
            }
            return key;
        }

        /**
         * <p>Artemis matches (see <a href="https://artemis.apache.org/components/artemis/documentation/latest/wildcard-syntax.html#wildcard-syntax">wildcard-syntax</a>
         * and {@code org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler#addressFrom()}) deal
         * with patterns which are deduced from an {@link ObjectName} <strong>and</strong> {@code methodName}, where
         * the method name depends on whether we get/set a JMX attribute or invoke a JMX operation.</p>
         *
         * <p>When determining a cache key for an address or a queue, we don't need the <em>method</em>, we
         * only need to know whether a queue/address has dedicated configuration. So we need to trim the method name
         * from a calculated address. We can do it only heuristically.</p>
         *
         * @param match
         * @param prefix prefix for the matches, defaults to {@code mops} ("managed operations")
         * @param delimiter usually defaults to a dot, but configurable using {@code <wildcard-addresses>/<delimiter>}
         * @return
         */
        private String stripOperation(String match, String prefix, char delimiter) {
            // see org.apache.activemq.artemis.core.server.management.ArtemisRbacInvocationHandler.addressFrom()
            String addressPrefix = prefix + delimiter + "address";
            String queuePrefix = prefix + delimiter + "queue";


            if (match != null && match.startsWith(addressPrefix)) {
                // mops.address.<address name which may contain dots>[.operationName]
                return stripLastWordIfNeeded(match, delimiter, ArtemisUtils.addressAttributes, ArtemisUtils.addressOperations);
            }
            if (match != null && match.startsWith(queuePrefix)) {
                // mops.queue.<queue name which may contain dots>[.operationName]
                return stripLastWordIfNeeded(match, delimiter, ArtemisUtils.queueAttributes, ArtemisUtils.queueOperations);
            }
            return match;
        }

        /**
         * Some matches in {@code etc/broker.xml} may use operation names and some don't - we are not interested
         * in operation names for list caching purposes.
         *
         * @param match
         * @param delimiter
         * @param attributeMethods
         * @param operationMethods
         * @return
         */
        private String stripLastWordIfNeeded(String match, char delimiter, Set<String> attributeMethods, Set<String> operationMethods) {
            String[] split = match.split(Pattern.quote(Character.toString(delimiter)));
            String last = split.length > 0 ? split[split.length - 1] : null;
            if (last != null) {
                if ("#".equals(last)) {
                    // multi word trailing, nothing to strip
                    return match;
                }
                if ("*".equals(last)) {
                    // this is tricky, but we _have to_ assume something - is it "any method" or "a queue/address
                    // with wildcard last word"? We assume it's a ... method
                    return Arrays.stream(split, 0, split.length - 1).collect(Collectors.joining(Character.toString(delimiter)));
                }
                if (attributeMethods.contains(last) || operationMethods.contains(last)) {
                    return Arrays.stream(split, 0, split.length - 1).collect(Collectors.joining(Character.toString(delimiter)));
                }
            }
            return last;
        }
    }

    /**
     * See {@code org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository.MatchComparator}
     */
    private static final class MatchComparator implements Comparator<String> {

        private final String quotedDelimiter;
        private final String anyWords;
        private final String singleWord;

        MatchComparator(final WildcardConfiguration wildcardConfiguration) {
            this.quotedDelimiter = Pattern.quote(wildcardConfiguration.getDelimiterString());
            this.singleWord = wildcardConfiguration.getSingleWordString();
            this.anyWords = wildcardConfiguration.getAnyWordsString();
        }

        @Override
        public int compare(final String o1, final String o2) {
            if (o1.contains(anyWords) && !o2.contains(anyWords)) {
                return +1;
            } else if (!o1.contains(anyWords) && o2.contains(anyWords)) {
                return -1;
            } else if (o1.contains(anyWords) && o2.contains(anyWords)) {
                return o2.length() - o1.length();
            } else if (o1.contains(singleWord) && !o2.contains(singleWord)) {
                return +1;
            } else if (!o1.contains(singleWord) && o2.contains(singleWord)) {
                return -1;
            } else if (o1.contains(singleWord) && o2.contains(singleWord)) {
                String[] leftSplits = o1.split(quotedDelimiter);
                String[] rightSplits = o2.split(quotedDelimiter);
                for (int i = 0; i < leftSplits.length; i++) {
                    if (i >= rightSplits.length) {
                        return -1;
                    }
                    String left = leftSplits[i];
                    String right = rightSplits[i];
                    if (left.equals(singleWord) && !right.equals(singleWord)) {
                        return +1;
                    } else if (!left.equals(singleWord) && right.equals(singleWord)) {
                        return -1;
                    }
                }
            }
            return o1.length() - o2.length();
        }
    }

}

/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.security.core.standards;

import io.netty.security.core.Action;
import io.netty.security.core.IpAddresses;
import io.netty.security.core.Ports;
import io.netty.security.core.Protocol;
import io.netty.security.core.Rule;
import io.netty.security.core.payload.Payload;
import io.netty.security.core.payload.PayloadMatcher;
import io.netty.util.internal.ObjectUtil;

import java.util.List;

import static io.netty.security.core.Util.compareIntegers;
import static io.netty.security.core.Util.hash;

public class StandardRule implements Rule {
    private final Protocol protocol;
    private final Ports sourcePorts;
    private final Ports destinationPorts;
    private final IpAddresses sourceIpAddresses;
    private final IpAddresses destinationIpAddress;
    private final List<? extends Payload<?>> payloads;
    private final PayloadMatcher<Object, Object> payloadMatcher;
    private final Action action;

    StandardRule(Protocol protocol, Ports sourcePorts, Ports destinationPorts, IpAddresses sourceIpAddresses,
                 IpAddresses destinationIpAddress, List<? extends Payload<?>> payloads,
                 PayloadMatcher<Object, Object> payloadMatcher, Action action) {
        this.protocol = ObjectUtil.checkNotNull(protocol, "Protocol");
        this.sourcePorts = ObjectUtil.checkNotNull(sourcePorts, "SourcePorts");
        this.destinationPorts = ObjectUtil.checkNotNull(destinationPorts, "DestinationPorts");
        this.sourceIpAddresses = ObjectUtil.checkNotNull(sourceIpAddresses, "SourceIPAddresses");
        this.destinationIpAddress = ObjectUtil.checkNotNull(destinationIpAddress, "DestinationIpAddresses");
        this.payloads = ObjectUtil.checkNotNull(payloads, "Payloads");
        this.payloadMatcher = ObjectUtil.checkNotNull(payloadMatcher, "PayloadMatcher");
        this.action = ObjectUtil.checkNotNull(action, "Action");
    }

    /**
     * Create a new {@link StandardRuleBuilder} instance for building new {@link Rule}
     * with 'Accept Any' set to {@link Boolean#TRUE}
     *
     * @return new {@link StandardRuleBuilder} instance
     */
    public static StandardRuleBuilder newBuilder() {
        return new StandardRuleBuilder(true);
    }

    /**
     * Create a new {@link StandardRuleBuilder} instance for building new {@link Rule}
     * with specified property of 'Accept Any'.
     *
     * @param acceptAny Set to {@link Boolean#TRUE} to accept any property else
     *                  set to {@link Boolean#FALSE}
     * @return new {@link StandardRuleBuilder} instance
     */
    public static StandardRuleBuilder newBuilder(boolean acceptAny) {
        return new StandardRuleBuilder(acceptAny);
    }

    @Override
    public Protocol protocol() {
        return protocol;
    }

    @Override
    public Ports sourcePorts() {
        return sourcePorts;
    }

    @Override
    public Ports destinationPorts() {
        return destinationPorts;
    }

    @Override
    public IpAddresses sourceIpAddresses() {
        return sourceIpAddresses;
    }

    @Override
    public IpAddresses destinationIpAddresses() {
        return destinationIpAddress;
    }

    @Override
    public List<? extends Payload<?>> payloads() {
        return payloads;
    }

    @Override
    public PayloadMatcher<Object, Object> payloadMatcher() {
        return payloadMatcher;
    }

    @Override
    public Action action() {
        return action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StandardRule that = (StandardRule) o;
        return hashCode() == that.hashCode();
    }

    /**
     * Compare a {@link Rule} with this instance
     *
     * @param rule the object to be compared.
     * @return 0 if this rule matches
     */
    @Override
    public int compareTo(Rule rule) {
        // Protocol
        int compare = compareIntegers(protocol().ordinal(), rule.protocol().ordinal());
        if (compare != 0) {
            return compare;
        }

        // Source Port
        compare = sourcePorts().compareTo(rule.sourcePorts());
        if (compare != 0) {
            return compare;
        }

        // Destination Port
        compare = destinationPorts().compareTo(rule.destinationPorts());
        if (compare != 0) {
            return compare;
        }

        // Source IP Address
        compare = sourceIpAddresses().compareTo(rule.sourceIpAddresses());
        if (compare != 0) {
            return compare;
        }

        // Destination IP Address
        return destinationIpAddresses().compareTo(rule.destinationIpAddresses());
    }

    @Override
    public int hashCode() {
        return hash(protocol, sourcePorts, destinationPorts, sourceIpAddresses,
                destinationIpAddress, payloads, payloadMatcher, action);
    }

    @Override
    public String toString() {
        return "StandardRule{" +
                "protocol=" + protocol +
                ", sourcePorts=" + sourcePorts +
                ", destinationPorts=" + destinationPorts +
                ", sourceIpAddresses=" + sourceIpAddresses +
                ", destinationIpAddress=" + destinationIpAddress +
                ", matchType=" + payloads +
                ", payloadMatcher=" + payloadMatcher +
                ", action=" + action +
                '}';
    }
}

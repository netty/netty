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
import io.netty.security.core.payload.Payload;
import io.netty.security.core.payload.PayloadMatcher;

import java.util.Collections;
import java.util.List;

public final class StandardRuleBuilder {
    private static final List<? extends Payload<?>> EMPTY_PAYLOAD = Collections.singletonList(Payload.NULL_PAYLOAD);

    private Protocol protocol;
    private Ports sourcePorts;
    private Ports destinationPorts;
    private IpAddresses sourceIpAddresses;
    private IpAddresses destinationIpAddress;
    private List<? extends Payload<?>> payloads;
    private PayloadMatcher<Object, Object> payloadMatcher;
    private Action action;

    /**
     * Create a new {@link StandardRuleBuilder} instance
     */
    StandardRuleBuilder() {
        this(false);
    }

    /**
     * Create a new {@link StandardRuleBuilder} instance
     *
     * @param acceptAny Set to {@link Boolean#TRUE} to accept any port, ip address or payload.
     */
    StandardRuleBuilder(boolean acceptAny) {
        // Package access only
        if (acceptAny) {
            sourcePorts = Ports.ANYPORT;
            destinationPorts = Ports.ANYPORT;
            sourceIpAddresses = IpAddresses.AcceptAnyIpAddresses.INSTANCE;
            destinationIpAddress = IpAddresses.AcceptAnyIpAddresses.INSTANCE;
            payloads = EMPTY_PAYLOAD;
            payloadMatcher = PayloadMatcher.ANY_PAYLOAD;
        }
    }

    public StandardRuleBuilder withProtocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public StandardRuleBuilder withSourcePorts(Ports sourcePorts) {
        this.sourcePorts = sourcePorts;
        return this;
    }

    public StandardRuleBuilder withDestinationPorts(Ports destinationPorts) {
        this.destinationPorts = destinationPorts;
        return this;
    }

    public StandardRuleBuilder withSourceIpAddresses(IpAddresses sourceIpAddresses) {
        this.sourceIpAddresses = sourceIpAddresses;
        return this;
    }

    public StandardRuleBuilder withDestinationIpAddress(IpAddresses destinationIpAddress) {
        this.destinationIpAddress = destinationIpAddress;
        return this;
    }

    public StandardRuleBuilder withPayloads(List<? extends Payload<?>> payloads) {
        this.payloads = payloads;
        return this;
    }

    public StandardRuleBuilder withPayloadMatcher(PayloadMatcher<Object, Object> payloadMatcher) {
        this.payloadMatcher = payloadMatcher;
        return this;
    }

    public StandardRuleBuilder withAction(Action action) {
        this.action = action;
        return this;
    }

    /**
     * Build new {@link StandardRule} instance
     */
    public StandardRule build() {
        return new StandardRule(protocol, sourcePorts, destinationPorts, sourceIpAddresses, destinationIpAddress,
                payloads, payloadMatcher, action);
    }
}

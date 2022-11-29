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
package io.netty.security.core;

import io.netty.security.core.payload.Payload;
import io.netty.security.core.payload.PayloadMatcher;

import java.util.List;

public interface Rule extends Comparable<Rule> {

    /**
     * Tuple {@link Protocol}
     */
    Protocol protocol();

    /**
     * Source Ports
     */
    Ports sourcePorts();

    /**
     * Destination Ports
     */
    Ports destinationPorts();

    /**
     * Rule Source {@link IpAddresses}
     */
    IpAddresses sourceIpAddresses();

    /**
     * Rule Destination {@link IpAddresses}
     */
    IpAddresses destinationIpAddresses();

    /**
     * {@link List} of {@link Payload}
     */
    List<? extends Payload<?>> payloads();

    /**
     * Rule {@link PayloadMatcher}
     */
    PayloadMatcher<Object, Object> payloadMatcher();

    /**
     * Rule {@link Action}
     */
    Action action();

    @Override
    int compareTo(Rule rule);
}

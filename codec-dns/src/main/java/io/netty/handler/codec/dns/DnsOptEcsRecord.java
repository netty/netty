/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.dns;

import java.net.InetAddress;

/**
 * An ECS record as defined in <a href="https://tools.ietf.org/html/rfc7871#section-6">Client Subnet in DNS Queries</a>.
 */
public interface DnsOptEcsRecord extends DnsOptPseudoRecord {

    /**
     * Returns the leftmost number of significant bits of ADDRESS to be used for the lookup.
     */
    int sourcePrefixLength();

    /**
     * Returns the leftmost number of significant bits of ADDRESS that the response covers.
     * In queries, it MUST be 0.
     */
    int scopePrefixLength();

    /**
     * Returns the bytes of the {@link InetAddress} to use.
     */
    byte[] address();
}

/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import io.netty.util.ReferenceCounted;

/**
 * The default {@link DnsQuery} implementation.
 */
public class DefaultDnsQuery<M extends ReferenceCounted & DnsQuery<M>>
        extends AbstractDnsMessage<M> implements DnsQuery<M> {

    /**
     * Creates a new instance with the {@link DnsOpCode#QUERY} {@code opCode}.
     *
     * @param id the {@code ID} of the DNS query
     */
    public DefaultDnsQuery(int id) {
        super(id);
    }

    /**
     * Creates a new instance.
     *
     * @param id the {@code ID} of the DNS query
     * @param opCode the {@code opCode} of the DNS query
     */
    public DefaultDnsQuery(int id, DnsOpCode opCode) {
        super(id, opCode);
    }

    @Override
    public String toString() {
        return DnsMessageUtil.appendQuery(new StringBuilder(128), this).toString();
    }
}

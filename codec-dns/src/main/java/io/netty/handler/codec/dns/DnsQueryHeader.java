/*
 * Copyright 2013 The Netty Project
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

/**
 * The DNS query header class which is used to represent the 12 byte header in a
 * {@link DnsQuery}.
 */
public final class DnsQueryHeader extends DnsHeader {

    /**
     * Constructor for a DNS packet query header. The id is user generated and
     * will be replicated in the response packet by the server.
     *
     * @param parent the {@link DnsMessage} this header belongs to
     * @param id     a 2 bit unsigned identification number for this query
     */
    public DnsQueryHeader(DnsMessage<DnsQueryHeader> parent, int id) {
        super(parent);
        setId(id);
        setRecursionDesired(true);
    }

    /**
     * Returns the {@link DnsMessage} type. This will always return
     * {@code TYPE_QUERY}.
     */
    @Override
    public int type() {
        return TYPE_QUERY;
    }

    /**
     * Sets the {@link DnsHeader} type. Must be {@code TYPE_RESPONSE}.
     *
     * @param type message type
     * @return the header to allow method chaining
     */
    @Override
    public DnsQueryHeader setType(int type) {
        if (type != TYPE_QUERY) {
            throw new IllegalArgumentException("type cannot be anything but TYPE_QUERY (0) for a query header.");
        }
        super.setType(type);
        return this;
    }

    @Override
    public DnsQueryHeader setId(int id) {
        super.setId(id);
        return this;
    }

    @Override
    public DnsQueryHeader setRecursionDesired(boolean recursionDesired) {
        super.setRecursionDesired(recursionDesired);
        return this;
    }

    @Override
    public DnsQueryHeader setOpcode(int opcode) {
        super.setOpcode(opcode);
        return this;
    }

    @Override
    public DnsQueryHeader setZ(int z) {
        super.setZ(z);
        return this;
    }
}

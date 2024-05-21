/*
 * Copyright 2015 The Netty Project
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

import io.netty.channel.AddressedEnvelope;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * A {@link DnsQuery} implementation for UDP/IP.
 */
public class DatagramDnsQuery extends DefaultDnsQuery
        implements AddressedEnvelope<DatagramDnsQuery, InetSocketAddress> {

    private final InetSocketAddress sender;
    private final InetSocketAddress recipient;

    /**
     * Creates a new instance with the {@link DnsOpCode#QUERY} {@code opCode}.
     *
     * @param sender the address of the sender
     * @param recipient the address of the recipient
     * @param id the {@code ID} of the DNS query
     */
    public DatagramDnsQuery(
            InetSocketAddress sender, InetSocketAddress recipient, int id) {
        this(sender, recipient, id, DnsOpCode.QUERY);
    }

    /**
     * Creates a new instance.
     *
     * @param sender the address of the sender
     * @param recipient the address of the recipient
     * @param id the {@code ID} of the DNS query
     * @param opCode the {@code opCode} of the DNS query
     */
    public DatagramDnsQuery(
            InetSocketAddress sender, InetSocketAddress recipient, int id, DnsOpCode opCode) {
        super(id, opCode);

        if (recipient == null && sender == null) {
            throw new NullPointerException("recipient and sender");
        }

        this.sender = sender;
        this.recipient = recipient;
    }

    @Override
    public DatagramDnsQuery content() {
        return this;
    }

    @Override
    public InetSocketAddress sender() {
        return sender;
    }

    @Override
    public InetSocketAddress recipient() {
        return recipient;
    }

    @Override
    public DatagramDnsQuery setId(int id) {
        return (DatagramDnsQuery) super.setId(id);
    }

    @Override
    public DatagramDnsQuery setOpCode(DnsOpCode opCode) {
        return (DatagramDnsQuery) super.setOpCode(opCode);
    }

    @Override
    public DatagramDnsQuery setRecursionDesired(boolean recursionDesired) {
        return (DatagramDnsQuery) super.setRecursionDesired(recursionDesired);
    }

    @Override
    public DatagramDnsQuery setZ(int z) {
        return (DatagramDnsQuery) super.setZ(z);
    }

    @Override
    public DatagramDnsQuery setRecord(DnsSection section, DnsRecord record) {
        return (DatagramDnsQuery) super.setRecord(section, record);
    }

    @Override
    public DatagramDnsQuery addRecord(DnsSection section, DnsRecord record) {
        return (DatagramDnsQuery) super.addRecord(section, record);
    }

    @Override
    public DatagramDnsQuery addRecord(DnsSection section, int index, DnsRecord record) {
        return (DatagramDnsQuery) super.addRecord(section, index, record);
    }

    @Override
    public DatagramDnsQuery clear(DnsSection section) {
        return (DatagramDnsQuery) super.clear(section);
    }

    @Override
    public DatagramDnsQuery clear() {
        return (DatagramDnsQuery) super.clear();
    }

    @Override
    public DatagramDnsQuery touch() {
        return (DatagramDnsQuery) super.touch();
    }

    @Override
    public DatagramDnsQuery touch(Object hint) {
        return (DatagramDnsQuery) super.touch(hint);
    }

    @Override
    public DatagramDnsQuery retain() {
        return (DatagramDnsQuery) super.retain();
    }

    @Override
    public DatagramDnsQuery retain(int increment) {
        return (DatagramDnsQuery) super.retain(increment);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!super.equals(obj)) {
            return false;
        }

        if (!(obj instanceof AddressedEnvelope)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        final AddressedEnvelope<?, SocketAddress> that = (AddressedEnvelope<?, SocketAddress>) obj;
        if (sender() == null) {
            if (that.sender() != null) {
                return false;
            }
        } else if (!sender().equals(that.sender())) {
            return false;
        }

        if (recipient() == null) {
            if (that.recipient() != null) {
                return false;
            }
        } else if (!recipient().equals(that.recipient())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        if (sender() != null) {
            hashCode = hashCode * 31 + sender().hashCode();
        }
        if (recipient() != null) {
            hashCode = hashCode * 31 + recipient().hashCode();
        }
        return hashCode;
    }
}

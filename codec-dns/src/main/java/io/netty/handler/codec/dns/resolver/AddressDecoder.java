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
package io.netty.handler.codec.dns.resolver;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.util.CharsetUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Decodes A and AAAA resource records into IPv4 and IPv6 addresses,
 * respectively.
 */
public final class AddressDecoder implements DnsResourceDecoder<InetAddress> {

    private final int octets;

    /**
     * Constructs an {@code AddressDecoder}, which decodes A and AAAA resource
     * records.
     *
     * @param octets
     *            the number of octets an address has. 4 for type A records and
     *            16 for type AAAA records
     */
    public AddressDecoder(int octets) {
        this.octets = octets;
    }

    /**
     * Returns an {@link InetAddress} containing a decoded address from either an A
     * or AAAA resource record.
     *
     * @param resource
     *            the {@link DnsResource} being decoded
     */
    @Override
    public InetAddress decode(DnsResource resource) {
        ByteBuf data = resource.content();
        int size = data.readableBytes();
        if (size != octets) {
            throw new DecoderException("Invalid content length, or reader index when decoding address [index: "
                    + data.readerIndex() + ", expected length: " + octets + ", actual: " + size + "].");
        }
        byte[] address = new byte[octets];
        data.readBytes(address);
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new DecoderException("Could not convert address "
                    + data.toString(data.readerIndex(), size, CharsetUtil.UTF_8) + " to InetAddress.");
        }
    }
}

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

import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.util.Arrays;

/**
 * Default {@link DnsOptEcsRecord} implementation.
 */
@UnstableApi
public final class DefaultDnsOptEcsRecord extends AbstractDnsOptPseudoRrRecord implements DnsOptEcsRecord {
    private final int srcPrefixLength;
    private final byte[] address;

    /**
     * Creates a new instance.
     *
     * @param maxPayloadSize the suggested max payload size in bytes
     * @param extendedRcode the extended rcode
     * @param version the version
     * @param srcPrefixLength the prefix length
     * @param address the bytes of the {@link InetAddress} to use
     */
    public DefaultDnsOptEcsRecord(int maxPayloadSize, int extendedRcode, int version,
                                  int srcPrefixLength, byte[] address) {
        super(maxPayloadSize, extendedRcode, version);
        this.srcPrefixLength = srcPrefixLength;
        this.address = verifyAddress(address).clone();
    }

    /**
     * Creates a new instance.
     *
     * @param maxPayloadSize the suggested max payload size in bytes
     * @param srcPrefixLength the prefix length
     * @param address the bytes of the {@link InetAddress} to use
     */
    public DefaultDnsOptEcsRecord(int maxPayloadSize, int srcPrefixLength, byte[] address) {
        this(maxPayloadSize, 0, 0, srcPrefixLength, address);
    }

    /**
     * Creates a new instance.
     *
     * @param maxPayloadSize the suggested max payload size in bytes
     * @param protocolFamily the {@link InternetProtocolFamily} to use. This should be the same as the one used to
     *                       send the query.
     */
    public DefaultDnsOptEcsRecord(int maxPayloadSize, InternetProtocolFamily protocolFamily) {
        this(maxPayloadSize, 0, 0, 0, protocolFamily.localhost().getAddress());
    }

    private static byte[] verifyAddress(byte[] bytes) {
        if (bytes.length == 4 || bytes.length == 16) {
            return bytes;
        }
        throw new IllegalArgumentException("bytes.length must either 4 or 16");
    }

    @Override
    public int sourcePrefixLength() {
        return srcPrefixLength;
    }

    @Override
    public int scopePrefixLength() {
        return 0;
    }

    @Override
    public byte[] address() {
        return address.clone();
    }

    @Override
    public String toString() {
        StringBuilder sb = toStringBuilder();
        sb.setLength(sb.length() - 1);
        return sb.append(" address:")
          .append(Arrays.toString(address))
          .append(" sourcePrefixLength:")
          .append(sourcePrefixLength())
          .append(" scopePrefixLength:")
          .append(scopePrefixLength())
          .append(')').toString();
    }
}

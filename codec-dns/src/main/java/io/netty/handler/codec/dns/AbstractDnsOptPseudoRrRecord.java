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

import io.netty.util.internal.StringUtil;

/**
 * An <a href="https://tools.ietf.org/html/rfc6891#section-6.1">OPT RR</a> record.
 *
 * This is used for <a href="https://tools.ietf.org/html/rfc6891#section-6.1.3">
 *     Extension Mechanisms for DNS (EDNS(0))</a>.
 */
public abstract class AbstractDnsOptPseudoRrRecord extends AbstractDnsRecord implements DnsOptPseudoRecord {

    protected AbstractDnsOptPseudoRrRecord(int maxPayloadSize, int extendedRcode, int version) {
        this(maxPayloadSize, extendedRcode, version, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param maxPayloadSize    the maximum UDP payload size that can be reassembled and delivered by this
     *                          endpoint's network stack, encoded into the {@code CLASS} field
     * @param extendedRcode     the upper 8 bits of the extended 12-bit RCODE; the lower 4 bits are carried
     *                          by the DNS message header
     * @param version           the EDNS version
     * @param flags             the 16-bit flags field, which holds {@code DO} and {@code Z}; only its
     *                          low 16 bits are used
     */
    protected AbstractDnsOptPseudoRrRecord(int maxPayloadSize, int extendedRcode, int version, int flags) {
        super(StringUtil.EMPTY_STRING, DnsRecordType.OPT, maxPayloadSize,
                packIntoLong(extendedRcode, version, flags));
    }

    protected AbstractDnsOptPseudoRrRecord(int maxPayloadSize) {
        super(StringUtil.EMPTY_STRING, DnsRecordType.OPT, maxPayloadSize, 0);
    }

    // See https://tools.ietf.org/html/rfc6891#section-6.1.3
    private static long packIntoLong(int extendedRcode, int version, int flags) {
        return ((extendedRcode & 0xffL) << 24 | (version & 0xffL) << 16 | (flags & 0xffffL)) & 0xFFFFFFFFL;
    }

    @Override
    public int extendedRcode() {
        return (short) (((int) timeToLive() >> 24) & 0xff);
    }

    @Override
    public int version() {
        return (short) (((int) timeToLive() >> 16) & 0xff);
    }

    @Override
    public int flags() {
        return (int) (timeToLive() & 0xffffL);
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    final StringBuilder toStringBuilder() {
        return new StringBuilder(64)
                .append(StringUtil.simpleClassName(this))
                .append('(')
                .append("OPT flags:")
                .append(flags())
                .append(" version:")
                .append(version())
                .append(" extendedRecode:")
                .append(extendedRcode())
                .append(" udp:")
                .append(dnsClass())
                .append(')');
    }
}

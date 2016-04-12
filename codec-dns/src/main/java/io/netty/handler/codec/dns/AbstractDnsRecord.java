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

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.net.IDN;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A skeletal implementation of {@link DnsRecord}.
 */
@UnstableApi
public abstract class AbstractDnsRecord implements DnsRecord {

    private final String name;
    private final DnsRecordType type;
    private final short dnsClass;
    private final long timeToLive;
    private int hashCode;

    /**
     * Creates a new {@link #CLASS_IN IN-class} record.
     *
     * @param name the domain name
     * @param type the type of the record
     * @param timeToLive the TTL value of the record
     */
    protected AbstractDnsRecord(String name, DnsRecordType type, long timeToLive) {
        this(name, type, CLASS_IN, timeToLive);
    }

    /**
     * Creates a new record.
     *
     * @param name the domain name
     * @param type the type of the record
     * @param dnsClass the class of the record, usually one of the following:
     *                 <ul>
     *                     <li>{@link #CLASS_IN}</li>
     *                     <li>{@link #CLASS_CSNET}</li>
     *                     <li>{@link #CLASS_CHAOS}</li>
     *                     <li>{@link #CLASS_HESIOD}</li>
     *                     <li>{@link #CLASS_NONE}</li>
     *                     <li>{@link #CLASS_ANY}</li>
     *                 </ul>
     * @param timeToLive the TTL value of the record
     */
    protected AbstractDnsRecord(String name, DnsRecordType type, int dnsClass, long timeToLive) {
        if (timeToLive < 0) {
            throw new IllegalArgumentException("timeToLive: " + timeToLive + " (expected: >= 0)");
        }
        // Convert to ASCII which will also check that the length is not too big.
        // See:
        //   - https://github.com/netty/netty/issues/4937
        //   - https://github.com/netty/netty/issues/4935
        this.name = appendTrailingDot(IDN.toASCII(checkNotNull(name, "name")));
        this.type = checkNotNull(type, "type");
        this.dnsClass = (short) dnsClass;
        this.timeToLive = timeToLive;
    }

    private static String appendTrailingDot(String name) {
        if (name.length() > 0 && name.charAt(name.length() - 1) != '.') {
            return name + '.';
        }
        return name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DnsRecordType type() {
        return type;
    }

    @Override
    public int dnsClass() {
        return dnsClass & 0xFFFF;
    }

    @Override
    public long timeToLive() {
        return timeToLive;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof DnsRecord)) {
            return false;
        }

        final DnsRecord that = (DnsRecord) obj;
        final int hashCode = this.hashCode;
        if (hashCode != 0 && hashCode != that.hashCode()) {
            return false;
        }

        return type().intValue() == that.type().intValue() &&
               dnsClass() == that.dnsClass() &&
               name().equals(that.name());
    }

    @Override
    public int hashCode() {
        final int hashCode = this.hashCode;
        if (hashCode != 0) {
            return hashCode;
        }

        return this.hashCode = name.hashCode() * 31 + type().intValue() * 31 + dnsClass();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(64);

        buf.append(StringUtil.simpleClassName(this))
           .append('(')
           .append(name())
           .append(' ')
           .append(timeToLive())
           .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                      .append(' ')
                      .append(type().name())
                      .append(')');

        return buf.toString();
    }
}

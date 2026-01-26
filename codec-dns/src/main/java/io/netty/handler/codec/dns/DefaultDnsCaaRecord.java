/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

import java.util.Arrays;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsCaaRecord} implementation.
 */
public final class DefaultDnsCaaRecord extends AbstractDnsRecord implements DnsCaaRecord {

    private final int flags;
    private final String tag;
    private final byte[] value;

    /**
     * Creates a new CAA record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param flags the flags field
     * @param tag the tag field
     * @param value the value bytes
     */
    public DefaultDnsCaaRecord(String name, int dnsClass, long timeToLive,
                               int flags, String tag, byte[] value) {
        super(name, DnsRecordType.CAA, dnsClass, timeToLive);
        this.flags = flags & 0xff;
        this.tag = checkNotNull(tag, "tag");
        this.value = checkNotNull(value, "value").clone();
    }

    @Override
    public int flags() {
        return flags;
    }

    @Override
    public String tag() {
        return tag;
    }

    @Override
    public byte[] value() {
        return value.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsCaaRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsCaaRecord that = (DnsCaaRecord) obj;
        return timeToLive() == that.timeToLive() &&
               flags == that.flags() &&
               tag.equalsIgnoreCase(that.tag()) &&
               Arrays.equals(value, that.value());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + flags;
        hashCode = 31 * hashCode + tag.toLowerCase().hashCode();
        hashCode = 31 * hashCode + Arrays.hashCode(value);
        return hashCode;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');
        buf.append(name().isEmpty() ? "<root>" : name())
           .append(' ')
           .append(timeToLive())
           .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                      .append(' ')
                      .append(type().name())
                      .append(' ')
                      .append(flags)
                      .append(' ')
                      .append(tag)
                      .append(' ')
                      .append(new String(value, CharsetUtil.US_ASCII))
                      .append(')');

        return buf.toString();
    }
}

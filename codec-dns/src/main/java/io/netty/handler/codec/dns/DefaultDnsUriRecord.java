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

import io.netty.util.internal.StringUtil;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsUriRecord} implementation.
 */
public final class DefaultDnsUriRecord extends AbstractDnsRecord implements DnsUriRecord {

    private final int priority;
    private final int weight;
    private final String target;

    /**
     * Creates a new URI record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param priority the priority
     * @param weight the weight
     * @param target the target URI
     */
    public DefaultDnsUriRecord(String name, int dnsClass, long timeToLive,
                               int priority, int weight, String target) {
        super(name, DnsRecordType.URI, dnsClass, timeToLive);
        this.priority = priority & 0xffff;
        this.weight = weight & 0xffff;
        this.target = checkNotNull(target, "target");
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public String target() {
        return target;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsUriRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsUriRecord that = (DnsUriRecord) obj;
        return timeToLive() == that.timeToLive() &&
               priority == that.priority() &&
               weight == that.weight() &&
               target.equals(that.target());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + priority;
        hashCode = 31 * hashCode + weight;
        hashCode = 31 * hashCode + target.hashCode();
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
                      .append(priority)
                      .append(' ')
                      .append(weight)
                      .append(' ')
                      .append(target)
                      .append(')');

        return buf.toString();
    }
}

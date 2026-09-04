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
 * The default {@link DnsMxRecord} implementation.
 */
public final class DefaultDnsMxRecord extends AbstractDnsRecord implements DnsMxRecord {

    private final int preference;
    private final String exchange;

    /**
     * Creates a new MX record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param preference the preference value
     * @param exchange the mail exchange hostname
     */
    public DefaultDnsMxRecord(String name, int dnsClass, long timeToLive, int preference, String exchange) {
        super(name, DnsRecordType.MX, dnsClass, timeToLive);
        this.preference = preference;
        this.exchange = checkNotNull(exchange, "exchange");
    }

    @Override
    public int preference() {
        return preference;
    }

    @Override
    public String exchange() {
        return exchange;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsMxRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsMxRecord that = (DnsMxRecord) obj;
        return timeToLive() == that.timeToLive() &&
               preference == that.preference() &&
               exchange.equalsIgnoreCase(that.exchange());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + preference;
        hashCode = 31 * hashCode + exchange.toLowerCase().hashCode();
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
                      .append(preference)
                      .append(' ')
                      .append(exchange)
                      .append(')');

        return buf.toString();
    }
}

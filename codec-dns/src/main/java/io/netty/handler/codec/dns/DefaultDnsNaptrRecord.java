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
 * The default {@link DnsNaptrRecord} implementation.
 */
public final class DefaultDnsNaptrRecord extends AbstractDnsRecord implements DnsNaptrRecord {

    private final int order;
    private final int preference;
    private final byte[] flags;
    private final byte[] services;
    private final byte[] regexp;
    private final String replacement;

    /**
     * Creates a new NAPTR record with byte array fields.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param order the order
     * @param preference the preference
     * @param flags the flags as raw bytes
     * @param services the services as raw bytes
     * @param regexp the regexp as raw bytes
     * @param replacement the replacement domain name
     */
    public DefaultDnsNaptrRecord(String name, int dnsClass, long timeToLive,
                                 int order, int preference, byte[] flags, byte[] services,
                                 byte[] regexp, String replacement) {
        super(name, DnsRecordType.NAPTR, dnsClass, timeToLive);
        this.order = order & 0xffff;
        this.preference = preference & 0xffff;
        this.flags = checkNotNull(flags, "flags").clone();
        this.services = checkNotNull(services, "services").clone();
        this.regexp = checkNotNull(regexp, "regexp").clone();
        this.replacement = checkNotNull(replacement, "replacement");
    }

    /**
     * Creates a new NAPTR record with US-ASCII string fields.
     * <p>
     * This is a convenience constructor that converts the string parameters to bytes using US-ASCII encoding.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param order the order
     * @param preference the preference
     * @param flags the flags as a US-ASCII string
     * @param services the services as a US-ASCII string
     * @param regexp the regexp as a US-ASCII string
     * @param replacement the replacement domain name
     */
    public DefaultDnsNaptrRecord(String name, int dnsClass, long timeToLive,
                                 int order, int preference, String flags, String services,
                                 String regexp, String replacement) {
        this(name, dnsClass, timeToLive, order, preference,
             checkNotNull(flags, "flags").getBytes(CharsetUtil.US_ASCII),
             checkNotNull(services, "services").getBytes(CharsetUtil.US_ASCII),
             checkNotNull(regexp, "regexp").getBytes(CharsetUtil.US_ASCII),
             replacement);
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public int preference() {
        return preference;
    }

    @Override
    public byte[] flags() {
        return flags.clone();
    }

    @Override
    public byte[] services() {
        return services.clone();
    }

    @Override
    public byte[] regexp() {
        return regexp.clone();
    }

    @Override
    public String replacement() {
        return replacement;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsNaptrRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsNaptrRecord that = (DnsNaptrRecord) obj;
        return timeToLive() == that.timeToLive() &&
               order == that.order() &&
               preference == that.preference() &&
               Arrays.equals(flags, that.flags()) &&
               Arrays.equals(services, that.services()) &&
               Arrays.equals(regexp, that.regexp()) &&
               replacement.equalsIgnoreCase(that.replacement());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + order;
        hashCode = 31 * hashCode + preference;
        hashCode = 31 * hashCode + Arrays.hashCode(flags);
        hashCode = 31 * hashCode + Arrays.hashCode(services);
        hashCode = 31 * hashCode + Arrays.hashCode(regexp);
        hashCode = 31 * hashCode + replacement.toLowerCase().hashCode();
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
                      .append(order)
                      .append(' ')
                      .append(preference)
                      .append(' ')
                      .append(flagsAsString())
                      .append(' ')
                      .append(servicesAsString())
                      .append(' ')
                      .append(regexpAsString())
                      .append(' ')
                      .append(replacement)
                      .append(')');

        return buf.toString();
    }
}

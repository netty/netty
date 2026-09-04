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
 * The default {@link DnsSoaRecord} implementation.
 */
public final class DefaultDnsSoaRecord extends AbstractDnsRecord implements DnsSoaRecord {

    private final String mname;
    private final String rname;
    private final long serial;
    private final long refresh;
    private final long retry;
    private final long expire;
    private final long minimum;

    /**
     * Creates a new SOA record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param mname the primary nameserver for the zone
     * @param rname the mailbox of the responsible person
     * @param serial the serial number
     * @param refresh the refresh interval in seconds
     * @param retry the retry interval in seconds
     * @param expire the expire time in seconds
     * @param minimum the minimum TTL in seconds
     */
    public DefaultDnsSoaRecord(String name, int dnsClass, long timeToLive,
                               String mname, String rname,
                               long serial, long refresh, long retry, long expire, long minimum) {
        super(name, DnsRecordType.SOA, dnsClass, timeToLive);
        this.mname = checkNotNull(mname, "mname");
        this.rname = checkNotNull(rname, "rname");
        this.serial = serial & 0xffffffffL;
        this.refresh = refresh & 0xffffffffL;
        this.retry = retry & 0xffffffffL;
        this.expire = expire & 0xffffffffL;
        this.minimum = minimum & 0xffffffffL;
    }

    @Override
    public String mname() {
        return mname;
    }

    @Override
    public String rname() {
        return rname;
    }

    @Override
    public long serial() {
        return serial;
    }

    @Override
    public long refresh() {
        return refresh;
    }

    @Override
    public long retry() {
        return retry;
    }

    @Override
    public long expire() {
        return expire;
    }

    @Override
    public long minimum() {
        return minimum;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsSoaRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsSoaRecord that = (DnsSoaRecord) obj;
        return timeToLive() == that.timeToLive() &&
               mname.equalsIgnoreCase(that.mname()) &&
               rname.equalsIgnoreCase(that.rname()) &&
               serial == that.serial() &&
               refresh == that.refresh() &&
               retry == that.retry() &&
               expire == that.expire() &&
               minimum == that.minimum();
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + mname.toLowerCase().hashCode();
        hashCode = 31 * hashCode + rname.toLowerCase().hashCode();
        hashCode = 31 * hashCode + (int) (serial ^ (serial >>> 32));
        hashCode = 31 * hashCode + (int) (refresh ^ (refresh >>> 32));
        hashCode = 31 * hashCode + (int) (retry ^ (retry >>> 32));
        hashCode = 31 * hashCode + (int) (expire ^ (expire >>> 32));
        hashCode = 31 * hashCode + (int) (minimum ^ (minimum >>> 32));
        return hashCode;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(128).append(StringUtil.simpleClassName(this)).append('(');
        buf.append(name().isEmpty() ? "<root>" : name())
           .append(' ')
           .append(timeToLive())
           .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                      .append(' ')
                      .append(type().name())
                      .append(' ')
                      .append(mname)
                      .append(' ')
                      .append(rname)
                      .append(' ')
                      .append(serial)
                      .append(' ')
                      .append(refresh)
                      .append(' ')
                      .append(retry)
                      .append(' ')
                      .append(expire)
                      .append(' ')
                      .append(minimum)
                      .append(')');

        return buf.toString();
    }
}

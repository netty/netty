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

/**
 * The default {@link DnsLocRecord} implementation.
 */
public final class DefaultDnsLocRecord extends AbstractDnsRecord implements DnsLocRecord {

    private final int version;
    private final int size;
    private final int horizontalPrecision;
    private final int verticalPrecision;
    private final long latitude;
    private final long longitude;
    private final long altitude;

    /**
     * Creates a new LOC record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param version the version
     * @param size the size
     * @param horizontalPrecision the horizontal precision
     * @param verticalPrecision the vertical precision
     * @param latitude the latitude
     * @param longitude the longitude
     * @param altitude the altitude
     */
    public DefaultDnsLocRecord(String name, int dnsClass, long timeToLive,
                               int version, int size, int horizontalPrecision, int verticalPrecision,
                               long latitude, long longitude, long altitude) {
        super(name, DnsRecordType.LOC, dnsClass, timeToLive);
        this.version = version & 0xff;
        this.size = size & 0xff;
        this.horizontalPrecision = horizontalPrecision & 0xff;
        this.verticalPrecision = verticalPrecision & 0xff;
        this.latitude = latitude & 0xffffffffL;
        this.longitude = longitude & 0xffffffffL;
        this.altitude = altitude & 0xffffffffL;
    }

    @Override
    public int version() {
        return version;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int horizontalPrecision() {
        return horizontalPrecision;
    }

    @Override
    public int verticalPrecision() {
        return verticalPrecision;
    }

    @Override
    public long latitude() {
        return latitude;
    }

    @Override
    public long longitude() {
        return longitude;
    }

    @Override
    public long altitude() {
        return altitude;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsLocRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsLocRecord that = (DnsLocRecord) obj;
        return timeToLive() == that.timeToLive() &&
               version == that.version() &&
               size == that.size() &&
               horizontalPrecision == that.horizontalPrecision() &&
               verticalPrecision == that.verticalPrecision() &&
               latitude == that.latitude() &&
               longitude == that.longitude() &&
               altitude == that.altitude();
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + version;
        hashCode = 31 * hashCode + size;
        hashCode = 31 * hashCode + horizontalPrecision;
        hashCode = 31 * hashCode + verticalPrecision;
        hashCode = 31 * hashCode + (int) (latitude ^ (latitude >>> 32));
        hashCode = 31 * hashCode + (int) (longitude ^ (longitude >>> 32));
        hashCode = 31 * hashCode + (int) (altitude ^ (altitude >>> 32));
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
                      .append(version)
                      .append(' ')
                      .append(size)
                      .append(' ')
                      .append(horizontalPrecision)
                      .append(' ')
                      .append(verticalPrecision)
                      .append(' ')
                      .append(latitude)
                      .append(' ')
                      .append(longitude)
                      .append(' ')
                      .append(altitude)
                      .append(')');

        return buf.toString();
    }
}

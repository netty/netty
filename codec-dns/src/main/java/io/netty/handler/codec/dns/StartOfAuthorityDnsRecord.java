/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;
import java.nio.charset.Charset;

/**
 * Represents a DNS start-of-authority record.
 */
public final class StartOfAuthorityDnsRecord extends DnsEntry {

    private final String primaryNs;
    private final String adminMailbox;
    private final long serialNumber;
    private final long refreshInterval;
    private final long retryInterval;
    private final long expirationLimit;
    private final long minimumTimeToLive;

    public StartOfAuthorityDnsRecord(String name, String primaryNs,
            String adminMailbox, long serialNumber, long refreshInterval, long retryInterval,
            long expirationLimit, long minimumTtl, long timeToLive) {
        this(name, DnsType.SOA, DnsClass.IN, primaryNs, adminMailbox, serialNumber, refreshInterval,
                retryInterval, expirationLimit, minimumTtl, timeToLive);
    }

    public StartOfAuthorityDnsRecord(String name, DnsType type, String primaryNs,
            String adminMailbox, long serialNumber, long refreshInterval, long retryInterval,
            long expirationLimit, long minimumTtl, long timeToLive) {
        this(name, type, DnsClass.IN, primaryNs, adminMailbox, serialNumber, refreshInterval,
                retryInterval, expirationLimit, minimumTtl, timeToLive);
    }

    public StartOfAuthorityDnsRecord(String name, DnsType type, DnsClass dnsClass, String primaryNs,
            String adminMailbox, long serialNumber, long refreshInterval, long retryInterval,
            long expirationLimit, long minimumTtl, long timeToLive) {
        super(name, type, dnsClass, timeToLive);
        if (primaryNs == null) {
            throw new NullPointerException("primaryNs");
        }
        if (adminMailbox == null) {
            throw new NullPointerException("adminMailbox");
        }
        this.primaryNs = primaryNs;
        this.adminMailbox = adminMailbox;
        this.serialNumber = serialNumber;
        this.refreshInterval = refreshInterval;
        this.retryInterval = retryInterval;
        this.expirationLimit = expirationLimit;
        this.minimumTimeToLive = minimumTtl;
    }

    /**
     * @return the primaryNs
     */
    public String primaryNameserver() {
        return primaryNs;
    }

    /**
     * @return the adminMailbox
     */
    public String adminMailbox() {
        return adminMailbox;
    }

    /**
     * @return the serialNumber
     */
    public long serialNumber() {
        return serialNumber;
    }

    /**
     * @return the refreshInterval
     */
    public long refreshInterval() {
        return refreshInterval;
    }

    /**
     * @return the retryInterval
     */
    public long retryInterval() {
        return retryInterval;
    }

    /**
     * @return the expirationLimit
     */
    public long expirationLimit() {
        return expirationLimit;
    }

    /**
     * @return the minimumTtl
     */
    public long minimumTimeToLive() {
        return minimumTimeToLive;
    }

    @Override
    protected void writePayload(NameWriter nameWriter, ByteBuf into, Charset charset) {
        nameWriter.writeName(primaryNs, into, charset);
        nameWriter.writeName(adminMailbox, into, charset);
        into.writeInt((int) serialNumber);
        into.writeInt((int) refreshInterval);
        into.writeInt((int) retryInterval);
        into.writeInt((int) expirationLimit);
        into.writeInt((int) minimumTimeToLive);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 47 * hash + (this.primaryNs != null ? this.primaryNs.hashCode() : 0);
        hash = 47 * hash + (this.adminMailbox != null ? this.adminMailbox.hashCode() : 0);
        hash = 47 * hash + (int) (this.serialNumber ^ (this.serialNumber >>> 32));
        hash = 47 * hash + (int) (this.refreshInterval ^ (this.refreshInterval >>> 32));
        hash = 47 * hash + (int) (this.retryInterval ^ (this.retryInterval >>> 32));
        hash = 47 * hash + (int) (this.expirationLimit ^ (this.expirationLimit >>> 32));
        hash = 47 * hash + (int) (this.minimumTimeToLive ^ (this.minimumTimeToLive >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (!(obj instanceof StartOfAuthorityDnsRecord)) {
            return false;
        } else if (!super.equals(obj)) {
            return false;
        }
        StartOfAuthorityDnsRecord other = (StartOfAuthorityDnsRecord) obj;
        if ((this.primaryNs == null) ? (other.primaryNs != null) : !this.primaryNs.equals(other.primaryNs)) {
            return false;
        }
        return adminMailbox.equals(other.adminMailbox) && serialNumber == other.serialNumber
                && refreshInterval == other.refreshInterval && retryInterval == other.retryInterval
                && expirationLimit == other.expirationLimit && minimumTimeToLive == other.minimumTimeToLive;
    }

    @Override
    public String toString() {
        return new StringBuilder(128).append(StringUtil.simpleClassName(this))
                .append("(name: ").append(name())
                .append(", type: ").append(type())
                .append(", class: ").append(dnsClass())
                .append(", ttl: ").append(timeToLive())
                .append(", primaryNameserver: ").append(primaryNameserver())
                .append(", adminMailbox: ").append(adminMailbox())
                .append(", serialNumber: ").append(serialNumber())
                .append(", refreshInterval: ").append(refreshInterval())
                .append(", retryInterval: ").append(retryInterval())
                .append(", expirationLimit: ").append(expirationLimit())
                .append(", minimumTimeToLive: ").append(minimumTimeToLive())
                .append(')').toString();
    }

    @Override
    public StartOfAuthorityDnsRecord withTimeToLive(long seconds) {
        return new StartOfAuthorityDnsRecord(name(), type(), dnsClass(), primaryNs, adminMailbox,
                serialNumber, refreshInterval, retryInterval, expirationLimit, minimumTimeToLive, seconds);
    }
}

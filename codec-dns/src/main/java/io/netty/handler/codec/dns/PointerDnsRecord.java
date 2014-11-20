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
 * Represents a DNS PTR record.
 */
public final class PointerDnsRecord extends DnsEntry {

    private String hostName;

    public PointerDnsRecord(String name, String hostName, long timeToLive) {
        this(name, DnsType.PTR, hostName, timeToLive);
    }

    public PointerDnsRecord(String name, DnsType type, String hostName, long timeToLive) {
        this(name, type, DnsClass.IN, hostName, timeToLive);
    }

    public PointerDnsRecord(String name, DnsType type, DnsClass dnsClass, String hostName, long timeToLive) {
        super(name, type, dnsClass, timeToLive);
        if (hostName == null) {
            throw new NullPointerException("hostName");
        }
        this.hostName = hostName;
    }

    /**
     * The host name this PTR record references.
     */
    public String hostName() {
        return hostName;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && o instanceof PointerDnsRecord
                && ((PointerDnsRecord) o).hostName.equals(hostName);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 23 * hash + (this.hostName != null ? this.hostName.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return new StringBuilder(128).append(StringUtil.simpleClassName(this))
                .append("(name: ").append(name())
                .append(", type: ").append(type())
                .append(", class: ").append(dnsClass())
                .append(", ttl: ").append(timeToLive())
                .append(", hostName: ").append(hostName())
                .append(')').toString();
    }

    @Override
    protected void writePayload(NameWriter nameWriter, ByteBuf into, Charset charset) {
        nameWriter.writeName(hostName, into, charset);
    }

    @Override
    public PointerDnsRecord withTimeToLive(long seconds) {
        return new PointerDnsRecord(name(), type(), dnsClass(), hostName, seconds);
    }
}

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
 * Represents an NS DNS record.
 */
public final class NameServerDnsRecord extends DnsEntry {

    private final String nameserver;

    public NameServerDnsRecord(String name, String nameserver, long ttl) {
        this(name, DnsType.NS, nameserver, ttl);
    }

    public NameServerDnsRecord(String name, DnsType type, String nameserver, long ttl) {
        this(name, type, DnsClass.IN, nameserver, ttl);
    }

    public NameServerDnsRecord(String name, DnsType type, DnsClass dnsClass, String nameserver, long ttl) {
        super(name, type, dnsClass, ttl);
        if (nameserver == null) {
            throw new NullPointerException("nameserver");
        }
        this.nameserver = nameserver;
    }

    public String nameserver() {
        return nameserver;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && o instanceof MailExchangerDnsRecord
                && ((NameServerDnsRecord) o).nameserver.equals(nameserver);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 23 * hash + (this.nameserver != null ? this.nameserver.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return new StringBuilder(128).append(StringUtil.simpleClassName(this))
                .append("(name: ").append(name())
                .append(", type: ").append(type())
                .append(", class: ").append(dnsClass())
                .append(", ttl: ").append(timeToLive())
                .append(", nameserver: ").append(nameserver())
                .append(')').toString();
    }

    @Override
    protected void writePayload(NameWriter nameWriter, ByteBuf into, Charset charset) {
        nameWriter.writeName(nameserver, into, charset);
    }
}

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
 * Represents a CNAME DNS record.
 */
public final class CNameDnsRecord extends DnsEntry {

    private final String cname;

    public CNameDnsRecord(String name, long timeToLive, String cname) {
        this(name, DnsType.CNAME, timeToLive, cname);
    }

    public CNameDnsRecord(String name, DnsType type, long timeToLive, String cname) {
        this(name, type, DnsClass.IN, timeToLive, cname);
    }

    public CNameDnsRecord(String name, DnsType type, DnsClass dnsClass, long timeToLive, String cname) {
        super(name, type, dnsClass, timeToLive);
        this.cname = cname;
    }

    public String cname() {
        return cname;
    }

    @Override
    protected void writePayload(NameWriter nameWriter, ByteBuf into, Charset charset) {
        nameWriter.writeName(cname, into, charset);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 83 * hash + (this.cname != null ? this.cname.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CNameDnsRecord other = (CNameDnsRecord) obj;
        if ((this.cname == null) ? (other.cname != null) : !this.cname.equals(other.cname)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return new StringBuilder(128).append(StringUtil.simpleClassName(this))
                .append("(name: ").append(name())
                .append(", type: ").append(type())
                .append(", class: ").append(dnsClass())
                .append(", ttl: ").append(timeToLive())
                .append(", cname: ").append(cname())
                .append(')').toString();
    }

    @Override
    public CNameDnsRecord withTimeToLive(long seconds) {
        return new CNameDnsRecord(cname, type(), dnsClass(), seconds, cname);
    }
}

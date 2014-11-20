/*
 * Copyright 2013 The Netty Project
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
 * A class representing entries in a DNS packet (questions, and all resource
 * records). Contains data shared by entries such as name, type, and class.
 */
public abstract class DnsEntry {

    private final String name;
    private final DnsType type;
    private final DnsClass dnsClass;
    private final long timeToLive;

    // only allow to extend from same package
    DnsEntry(String name, DnsType type, DnsClass dnsClass, long timeToLive) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (dnsClass == null) {
            throw new NullPointerException("dnsClass");
        }

        this.name = name;
        this.type = type;
        this.dnsClass = dnsClass;
        this.timeToLive = timeToLive;
    }

    /**
     * Number of seconds that a cache may retain this record
     * @return A number of seconds for which this entry may be treated as current
     */
    public final long timeToLive() {
        return timeToLive;
    }

    /**
     * Returns the name of this entry (the domain).
     */
    public String name() {
        return name;
    }

    /**
     * Returns the type of resource record to be received.
     */
    public DnsType type() {
        return type;
    }

    /**
     * Returns the class for this entry. Default is IN (Internet).
     */
    public DnsClass dnsClass() {
        return dnsClass;
    }

    /**
     * Create a transformed DnsEntry with the passed time-to-live
     * @param seconds The number of seconds
     * @return A new DnsEntry identical to this one other than that it
     * uses the passed time-to-live value
     */
    public abstract DnsEntry withTimeToLive(long seconds);

    @Override
    public int hashCode() {
        return (name.hashCode() * 31 + type.hashCode()) * 31 + dnsClass.hashCode();
    }

    @Override
    public String toString() {
        return new StringBuilder(128).append(StringUtil.simpleClassName(this))
                                     .append("(name: ").append(name)
                                     .append(", type: ").append(type)
                                     .append(", class: ").append(dnsClass)
                                     .append(", ttl: ").append(timeToLive)
                                     .append(')').toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DnsEntry)) {
            return false;
        }

        DnsEntry that = (DnsEntry) o;
        return type().intValue() == that.type().intValue() &&
               dnsClass().intValue() == that.dnsClass().intValue() &&
               name().equals(that.name()) && timeToLive() == that.timeToLive();
    }

    /**
     * Write this dns entry into a buffer
     *
     * @param nameWriter Writes names, possibly using pointers for compression
     * @param into The buffer to write into
     * @param charset The character set to use
     */
    public void writeTo(NameWriter nameWriter, ByteBuf into, Charset charset) {
        nameWriter.writeName(name(), into, charset);
        into.writeShort(type().intValue());
        into.writeShort(dnsClass().intValue());
        into.writeInt((int) timeToLive());
        // Remember where to put the length field once we know what it is
        int offset = into.writerIndex();
        // Write a placeholder value
        into.writeShort(0);
        try {
            writePayload(nameWriter, into, charset);
            // newOffset - offset = number of bytes written for the payload
            int newOffset = into.writerIndex();
            // Rewind to the length field position and update it
            into.writerIndex(offset);
            into.writeShort(newOffset - (offset + 2));
            // Fast forward back to the end of the buffer
            into.writerIndex(newOffset);
        } catch (RuntimeException ex) {
            // We may have written garbage, so dump it
            into.writerIndex(offset);
            into.writeShort(0);
            throw ex;
        }
    }

    /**
     * Write the <i>payload</i> of this DNS entry.  The format varies by
     * type.
     * @param nameWriter A thing which will write names
     * @param into The buffer to write into
     * @param charset The character set to use
     */
    protected void writePayload(NameWriter nameWriter, ByteBuf into, Charset charset) {
        throw new UnsupportedOperationException("Either override writeTo or writePayload in "
                + getClass().getSimpleName());
    }
}

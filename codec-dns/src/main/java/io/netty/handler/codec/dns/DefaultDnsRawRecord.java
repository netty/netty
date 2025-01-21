/*
 * Copyright 2015 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@code DnsRawRecord} implementation.
 */
public class DefaultDnsRawRecord extends AbstractDnsRecord implements DnsRawRecord {

    private final ByteBuf content;

    /**
     * Creates a new {@link #CLASS_IN IN-class} record.
     *
     * @param name the domain name
     * @param type the type of the record
     * @param timeToLive the TTL value of the record
     */
    public DefaultDnsRawRecord(String name, DnsRecordType type, long timeToLive, ByteBuf content) {
        this(name, type, DnsRecord.CLASS_IN, timeToLive, content);
    }

    /**
     * Creates a new record.
     *
     * @param name the domain name
     * @param type the type of the record
     * @param dnsClass the class of the record, usually one of the following:
     *                 <ul>
     *                     <li>{@link #CLASS_IN}</li>
     *                     <li>{@link #CLASS_CSNET}</li>
     *                     <li>{@link #CLASS_CHAOS}</li>
     *                     <li>{@link #CLASS_HESIOD}</li>
     *                     <li>{@link #CLASS_NONE}</li>
     *                     <li>{@link #CLASS_ANY}</li>
     *                 </ul>
     * @param timeToLive the TTL value of the record
     */
    public DefaultDnsRawRecord(
            String name, DnsRecordType type, int dnsClass, long timeToLive, ByteBuf content) {
        super(name, type, dnsClass, timeToLive);
        this.content = checkNotNull(content, "content");
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public DnsRawRecord copy() {
        return replace(content().copy());
    }

    @Override
    public DnsRawRecord duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public DnsRawRecord retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public DnsRawRecord replace(ByteBuf content) {
        return new DefaultDnsRawRecord(name(), type(), dnsClass(), timeToLive(), content);
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public DnsRawRecord retain() {
        content().retain();
        return this;
    }

    @Override
    public DnsRawRecord retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public boolean release() {
        return content().release();
    }

    @Override
    public boolean release(int decrement) {
        return content().release(decrement);
    }

    @Override
    public DnsRawRecord touch() {
        content().touch();
        return this;
    }

    @Override
    public DnsRawRecord touch(Object hint) {
        content().touch(hint);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');
        final DnsRecordType type = type();
        if (type != DnsRecordType.OPT) {
            buf.append(name().isEmpty()? "<root>" : name())
               .append(' ')
               .append(timeToLive())
               .append(' ');

            DnsMessageUtil.appendRecordClass(buf, dnsClass())
                          .append(' ')
                          .append(type.name());
        } else {
            buf.append("OPT flags:")
               .append(timeToLive())
               .append(" udp:")
               .append(dnsClass());
        }

        buf.append(' ')
           .append(content().readableBytes())
           .append("B)");

        return buf.toString();
    }
}

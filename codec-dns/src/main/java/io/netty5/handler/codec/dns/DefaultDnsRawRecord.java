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
package io.netty5.handler.codec.dns;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Send;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;

import static java.util.Objects.requireNonNull;

/**
 * The default {@code DnsRawRecord} implementation.
 */
@UnstableApi
public class DefaultDnsRawRecord extends AbstractDnsRecord implements DnsRawRecord {

    private final Buffer content;

    /**
     * Creates a new {@link #CLASS_IN IN-class} record.
     *
     * @param name the domain name
     * @param type the type of the record
     * @param timeToLive the TTL value of the record
     */
    public DefaultDnsRawRecord(String name, DnsRecordType type, long timeToLive, Buffer content) {
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
            String name, DnsRecordType type, int dnsClass, long timeToLive, Buffer content) {
        super(name, type, dnsClass, timeToLive);
        this.content = requireNonNull(content, "content");
    }

    public DefaultDnsRawRecord(
            String name, DnsRecordType type, int dnsClass, long timeToLive, Send<Buffer> content) {
        super(name, type, dnsClass, timeToLive);
        this.content = requireNonNull(content, "content").receive();
    }

    @Override
    public Buffer content() {
        return content;
    }

    public DnsRawRecord replace(Buffer content) {
        return new DefaultDnsRawRecord(name(), type(), dnsClass(), timeToLive(), content);
    }

    @Override
    public Send<DnsRawRecord> send() {
        return content.send().map(DnsRawRecord.class, this::replace);
    }

    @Override
    public void close() {
        content.close();
    }

    @Override
    public boolean isAccessible() {
        return content.isAccessible();
    }

    @Override
    public DnsRawRecord touch(Object hint) {
        content.touch(hint);
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

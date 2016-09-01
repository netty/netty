/*
 * Copyright 2016 The Netty Project
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

import java.util.regex.Pattern;

/**
 * Created by epenorr on 2016-08-06.
 */
public class DefaultDnsNaptrRecord extends AbstractDnsRecord {

    private final int order;

    private final int preference;

    private final String flags;

    private final String services;

    private final Pattern regexp;

    private final String replacement;

    public DefaultDnsNaptrRecord(String name, DnsRecordType type, int dnsClass, long timeToLive, ByteBuf in) {
        super(name, type, dnsClass, timeToLive);
        order = in.readUnsignedShort();
        preference = in.readUnsignedShort();
        flags = DefaultDnsRecordDecoder.decodeCharString(in);
        services = DefaultDnsRecordDecoder.decodeCharString(in);
        regexp = Pattern.compile(DefaultDnsRecordDecoder.decodeCharString(in));
        replacement = DefaultDnsRecordDecoder.decodeName(in);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');
        final DnsRecordType type = type();
        buf.append(name().isEmpty()? "<root>" : name())
                .append(' ')
                .append(timeToLive())
                .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                .append(' ')
                .append(type.name());

        buf.append(' ')
            .append(order)
            .append(' ')
            .append(preference)
            .append(' ')
            .append(flags)
            .append(' ')
            .append(services)
            .append(' ')
            .append(regexp.pattern())
            .append(' ')
            .append(replacement);

        return buf.toString();
    }

    public String flags() {
        return flags;
    }

    public int order() {
        return order;
    }

    public int preference() {
        return preference;
    }

    public Pattern regexp() {
        return regexp;
    }

    public String replacement() {
        return replacement;
    }

    public String services() {
        return services;
    }
}

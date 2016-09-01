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

/**
 * Created by epenorr on 2016-08-06.
 */
public class DefaultDnsSrvRecord extends AbstractDnsRecord {

    private final int priority;
    private final int weight;
    private final int port;
    private final String target;

    public DefaultDnsSrvRecord(String name, DnsRecordType type, int dnsClass, long timeToLive, ByteBuf in) {
        super(name, type, dnsClass, timeToLive);
        priority = in.readUnsignedShort();
        weight = in.readUnsignedShort();
        port = in.readUnsignedShort();
        target = DefaultDnsRecordDecoder.decodeName(in);
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
           .append(priority)
            .append(' ')
            .append(weight)
            .append(' ')
            .append(port)
            .append(' ')
            .append(target);

        return buf.toString();
    }

    public int port() {
        return port;
    }

    public int priority() {
        return priority;
    }

    public String target() {
        return target;
    }

    public int weight() {
        return weight;
    }
}

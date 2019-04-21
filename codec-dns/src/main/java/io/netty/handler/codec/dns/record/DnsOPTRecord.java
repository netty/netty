/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.dns.record;

import io.netty.handler.codec.dns.AbstractDnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.record.opt.EDNS0Option;
import io.netty.util.internal.StringUtil;

import java.util.Collections;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * Dns {@link DnsRecordType#OPT} record.
 */
public class DnsOPTRecord extends AbstractDnsRecord {
    private final List<EDNS0Option> options;

    public DnsOPTRecord(String name, int dnsClass, long timeToLive, List<EDNS0Option> options) {
        super(name, DnsRecordType.OPT, dnsClass, timeToLive);
        this.options = Collections.unmodifiableList(checkNotNull(options, "options"));
    }

    public List<EDNS0Option> options() {
        return options;
    }


    public byte extendedRcode() {
        return (byte) ((int) timeToLive() >> 24 & 0xff);
    }

    public byte version() {
        return (byte) ((int) timeToLive() >> 16 & 0xff);
    }

    public short flags() {
        return (short) ((short) timeToLive() & 0xffff);
    }

    public short udpSize() {
        return (short) dnsClass();
    }

    public boolean isDo() {
        short doMask = (short) (1 << 15);
        return (flags() & doMask) == doMask;
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    final StringBuilder toStringBuilder() {
        // Format options
        StringBuilder optionBuilder = new StringBuilder(32);
        optionBuilder.append("[");
        for (EDNS0Option option : options) {
            optionBuilder.append(option.optionCode());
            optionBuilder.append(", ");
        }
        if (!options.isEmpty()) {
            optionBuilder.delete(optionBuilder.length() - 2, optionBuilder.length());
        } else {
            optionBuilder.append("<EMPTY>");
        }
        optionBuilder.append("]");

        return new StringBuilder(64)
                .append(StringUtil.simpleClassName(this))
                .append('(')
                .append("OPT flags:")
                .append(isDo()? "Do" : "")
                .append(" version:")
                .append(version())
                .append(" extendedRecode:")
                .append(extendedRcode())
                .append(" udp:")
                .append(dnsClass())
                .append(" options:")
                .append(optionBuilder)
                .append(')');
    }

}

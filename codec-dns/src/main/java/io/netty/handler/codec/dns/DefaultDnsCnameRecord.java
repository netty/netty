/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.internal.StringUtil;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsCnameRecord} implementation.
 */
public class DefaultDnsCnameRecord extends AbstractDnsRecord implements DnsCnameRecord {

    private final String canonicalName;

    /**
     * Creates a new CNAME record.
     *
     * @param name the domain name
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
     * @param canonicalName the canonical name this record points to
     */
    public DefaultDnsCnameRecord(String name, int dnsClass, long timeToLive, String canonicalName) {
        super(name, DnsRecordType.CNAME, dnsClass, timeToLive);
        this.canonicalName = checkNotNull(canonicalName, "canonicalName");
    }

    @Override
    public String canonicalName() {
        return canonicalName;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');
        buf.append(name().isEmpty() ? "<root>" : name())
           .append(' ')
           .append(timeToLive())
           .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                      .append(' ')
                      .append(type().name())
                      .append(' ')
                      .append(canonicalName)
                      .append(')');

        return buf.toString();
    }
}

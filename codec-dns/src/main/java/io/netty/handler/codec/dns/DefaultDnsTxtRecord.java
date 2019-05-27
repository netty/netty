/*
 * Copyright 2018 The Netty Project
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

import io.netty.util.internal.StringUtil;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Default {@link DnsTxtRecord} implementation.
 */
public class DefaultDnsTxtRecord extends AbstractDnsRecord implements DnsTxtRecord {
    private final String key;
    private final String value;

    public DefaultDnsTxtRecord(String name, int dnsClass, long timeToLive, String key, String value) {
        super(name, DnsRecordType.TXT, dnsClass, timeToLive);

        this.key = checkNotNull(key, "key");
        this.value = checkNotNull(value, "value");
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String value() {
        return this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        if (!super.equals(o)) { return false; }
        DefaultDnsTxtRecord that = (DefaultDnsTxtRecord) o;
        return key.equals(that.key) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();

        hashCode = hashCode * 31 + key.hashCode();
        hashCode = hashCode * 31 + value.hashCode();

        return hashCode;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');

        buf.append(name().isEmpty()? "<root>" : name())
                .append(' ')
                .append(timeToLive())
                .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                .append(' ')
                .append(type().name());

        buf.append(' ')
                .append(key)
                .append(' ')
                .append(value);

        return buf.toString();
    }
}

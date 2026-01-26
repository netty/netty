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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsTxtRecord} implementation.
 */
public final class DefaultDnsTxtRecord extends AbstractDnsRecord implements DnsTxtRecord {

    private final List<String> texts;

    /**
     * Creates a new TXT record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param texts the TXT strings
     */
    public DefaultDnsTxtRecord(String name, int dnsClass, long timeToLive, String... texts) {
        this(name, dnsClass, timeToLive, texts == null ? null : Arrays.asList(texts));
    }

    /**
     * Creates a new TXT record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param texts the TXT strings
     */
    public DefaultDnsTxtRecord(String name, int dnsClass, long timeToLive, List<String> texts) {
        super(name, DnsRecordType.TXT, dnsClass, timeToLive);
        checkNotNull(texts, "texts");
        List<String> copy = new ArrayList<String>(texts.size());
        for (String text : texts) {
            copy.add(checkNotNull(text, "text"));
        }
        this.texts = Collections.unmodifiableList(copy);
    }

    @Override
    public List<String> texts() {
        return texts;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsTxtRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsTxtRecord that = (DnsTxtRecord) obj;
        return timeToLive() == that.timeToLive() &&
               texts.equals(that.texts());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + texts.hashCode();
        return hashCode;
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
                      .append(texts)
                      .append(')');

        return buf.toString();
    }
}

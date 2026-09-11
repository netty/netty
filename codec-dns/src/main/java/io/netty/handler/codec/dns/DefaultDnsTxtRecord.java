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

    private final List<byte[]> content;

    /**
     * Creates a new TXT record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param content the binary content entries (each up to 255 bytes)
     */
    public DefaultDnsTxtRecord(String name, int dnsClass, long timeToLive, byte[]... content) {
        this(name, dnsClass, timeToLive, content == null ? null : Arrays.asList(content));
    }

    /**
     * Creates a new TXT record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param content the binary content entries (each up to 255 bytes)
     */
    public DefaultDnsTxtRecord(String name, int dnsClass, long timeToLive, List<byte[]> content) {
        super(name, DnsRecordType.TXT, dnsClass, timeToLive);
        checkNotNull(content, "content");
        List<byte[]> copy = new ArrayList<byte[]>(content.size());
        for (byte[] entry : content) {
            copy.add(checkNotNull(entry, "entry").clone());
        }
        this.content = Collections.unmodifiableList(copy);
    }

    @Override
    public List<byte[]> content() {
        return content;
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
        if (timeToLive() != that.timeToLive()) {
            return false;
        }
        List<byte[]> thatContent = that.content();
        if (content.size() != thatContent.size()) {
            return false;
        }
        for (int i = 0; i < content.size(); i++) {
            if (!Arrays.equals(content.get(i), thatContent.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        for (byte[] entry : content) {
            hashCode = 31 * hashCode + Arrays.hashCode(entry);
        }
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
                      .append(" [");

        for (int i = 0; i < content.size(); i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(content.get(i).length).append(" bytes");
        }
        buf.append("])");

        return buf.toString();
    }
}

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

/**
 * The default {@link DnsQuery} implementation.
 */
public class DefaultDnsQuery extends AbstractDnsMessage implements DnsQuery {

    /**
     * Creates a new instance with the {@link DnsOpCode#QUERY} {@code opCode}.
     *
     * @param id the {@code ID} of the DNS query
     */
    public DefaultDnsQuery(int id) {
        super(id);
    }

    /**
     * Creates a new instance.
     *
     * @param id the {@code ID} of the DNS query
     * @param opCode the {@code opCode} of the DNS query
     */
    public DefaultDnsQuery(int id, DnsOpCode opCode) {
        super(id, opCode);
    }

    @Override
    public DnsQuery setId(int id) {
        return (DnsQuery) super.setId(id);
    }

    @Override
    public DnsQuery setOpCode(DnsOpCode opCode) {
        return (DnsQuery) super.setOpCode(opCode);
    }

    @Override
    public DnsQuery setRecursionDesired(boolean recursionDesired) {
        return (DnsQuery) super.setRecursionDesired(recursionDesired);
    }

    @Override
    public DnsQuery setZ(int z) {
        return (DnsQuery) super.setZ(z);
    }

    @Override
    public DnsQuery setRecord(DnsSection section, DnsRecord record) {
        return (DnsQuery) super.setRecord(section, record);
    }

    @Override
    public DnsQuery addRecord(DnsSection section, DnsRecord record) {
        return (DnsQuery) super.addRecord(section, record);
    }

    @Override
    public DnsQuery addRecord(DnsSection section, int index, DnsRecord record) {
        return (DnsQuery) super.addRecord(section, index, record);
    }

    @Override
    public DnsQuery clear(DnsSection section) {
        return (DnsQuery) super.clear(section);
    }

    @Override
    public DnsQuery clear() {
        return (DnsQuery) super.clear();
    }

    @Override
    public DnsQuery touch() {
        return (DnsQuery) super.touch();
    }

    @Override
    public DnsQuery touch(Object hint) {
        return (DnsQuery) super.touch(hint);
    }

    @Override
    public DnsQuery retain() {
        return (DnsQuery) super.retain();
    }

    @Override
    public DnsQuery retain(int increment) {
        return (DnsQuery) super.retain(increment);
    }

    @Override
    public String toString() {
        return DnsMessageUtil.appendQuery(new StringBuilder(128), this).toString();
    }
}

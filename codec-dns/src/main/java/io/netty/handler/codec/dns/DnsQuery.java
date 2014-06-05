/*
 * Copyright 2013 The Netty Project
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

import java.net.InetSocketAddress;

/**
 * A DNS query packet which is sent to a server to receive a DNS response packet
 * with information answering a DnsQuery's questions.
 */
public class DnsQuery extends DnsMessage<DnsQueryHeader> {

    private final InetSocketAddress recipient;

    /**
     * Constructs a DNS query. By default recursion will be toggled on.
     */
    public DnsQuery(int id, InetSocketAddress recipient) {
        super(id);
        if (recipient == null) {
            throw new NullPointerException("recipient");
        }
        this.recipient = recipient;
    }

    /**
     * Return the {@link InetSocketAddress} of the recipient of the {@link DnsQuery}
     */
    public InetSocketAddress recipient() {
        return recipient;
    }

    @Override
    public DnsQuery addAnswer(DnsResource answer) {
        super.addAnswer(answer);
        return this;
    }

    @Override
    public DnsQuery addQuestion(DnsQuestion question) {
        super.addQuestion(question);
        return this;
    }

    @Override
    public DnsQuery addAuthorityResource(DnsResource resource) {
        super.addAuthorityResource(resource);
        return this;
    }

    @Override
    public DnsQuery addAdditionalResource(DnsResource resource) {
        super.addAdditionalResource(resource);
        return this;
    }

    @Override
    public DnsQuery touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public DnsMessage<DnsQueryHeader> retain() {
        return super.retain();
    }

    @Override
    public DnsQuery retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public DnsQuery touch() {
        super.touch();
        return this;
    }

    @Override
    protected DnsQueryHeader newHeader(int id) {
        return new DnsQueryHeader(this, id);
    }
}

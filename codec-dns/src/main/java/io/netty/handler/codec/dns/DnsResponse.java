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
 * A DNS response packet which is sent to a client after a server receives a
 * query.
 */
public final class DnsResponse extends DnsMessage<DnsResponseHeader> {

    private final InetSocketAddress sender;

    public DnsResponse(int id, InetSocketAddress sender) {
        super(id);
        if (sender == null) {
            throw new NullPointerException("sender");
        }
        this.sender = sender;
    }

    /**
     * The {@link InetSocketAddress} of the sender of this {@link DnsResponse}
     */
    public InetSocketAddress sender() {
        return sender;
    }

    @Override
    public DnsResponse addAnswer(DnsResource answer) {
        super.addAnswer(answer);
        return this;
    }

    @Override
    public DnsResponse addQuestion(DnsQuestion question) {
        super.addQuestion(question);
        return this;
    }

    @Override
    public DnsResponse addAuthorityResource(DnsResource resource) {
        super.addAuthorityResource(resource);
        return this;
    }

    @Override
    public DnsResponse addAdditionalResource(DnsResource resource) {
        super.addAdditionalResource(resource);
        return this;
    }

    @Override
    public DnsResponse touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public DnsResponse retain() {
        super.retain();
        return this;
    }

    @Override
    public DnsResponse retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public DnsResponse touch() {
        super.touch();
        return this;
    }

    @Override
    protected DnsResponseHeader newHeader(int id) {
        return new DnsResponseHeader(this, id);
    }
}

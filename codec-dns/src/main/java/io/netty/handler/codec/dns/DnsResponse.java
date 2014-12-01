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

import io.netty.util.internal.StringUtil;
import java.net.InetSocketAddress;

/**
 * A DNS response packet which is sent to a client after a server receives a
 * query.
 */
public final class DnsResponse extends DnsMessage {

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
    public DnsResponse addAnswer(DnsEntry answer) {
        super.addAnswer(answer);
        return this;
    }

    @Override
    public DnsResponse addQuestions(Iterable<DnsQuestion> questions) {
        super.addQuestions(questions);
        return this;
    }

    @Override
    public DnsResponse addQuestion(DnsQuestion question) {
        super.addQuestion(question);
        return this;
    }

    @Override
    public DnsResponse addAuthorityResource(DnsEntry resource) {
        super.addAuthorityResource(resource);
        return this;
    }

    @Override
    public DnsResponse addAdditionalResource(DnsEntry resource) {
        super.addAdditionalResource(resource);
        return this;
    }

    @Override
    public DnsResponse touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public DnsResponseHeader header() {
        return (DnsResponseHeader) super.header();
    }

    @Override
    protected DnsResponseHeader newHeader(int id) {
        return new DnsResponseHeader(this, id);
    }

    /**
     * Copy the contents - questions, authorities and additional resources - of
     * this DnsResponse into another one, <i>not</i>
     * altering the ID or recipient of the other DnsResponse. Useful for proxy
     * servers.
     *
     * @param other A {@link DnsResponse} which should be altered to contain the
     * response resources of this one.
     */
    public void copyInto(DnsResponse other) {
        for (DnsQuestion question : questions()) {
            other.addQuestion(question);
        }
        for (DnsEntry answers : answers()) {
            other.addAnswer(duplicate(answers));
        }
        for (DnsEntry authority : authorityResources()) {
            other.addAuthorityResource(duplicate(authority));
        }
        for (DnsEntry additional : additionalResources()) {
            other.addAdditionalResource(duplicate(additional));
        }
        other.header()
                .setAuthoritativeAnswer(header().isAuthoritativeAnswer())
                .setResponseCode(header().responseCode())
                .setOpcode(header().opcode())
                .setRecursionAvailable(header().isRecursionAvailable())
                .setRecursionDesired(header().isRecursionDesired())
                .setType(header().type());
    }

    private DnsEntry duplicate(DnsEntry entry) {
        if (entry instanceof DnsResource) {
            return ((DnsResource) entry).duplicate();
        } else {
            return entry;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(80);
        sb.append(StringUtil.simpleClassName(DnsResponse.class)).append('@').append(
                System.identityHashCode(this)).append('{');
        sb.append("header=").append(header()).append(", answers=[");
        for (DnsEntry ans : answers()) {
            sb.append(ans).append(" ");
        }
        sb.append("], authorities=[");
        for (DnsEntry ans : authorityResources()) {
            sb.append(ans).append(" ");
        }
        sb.append("], additional=[");
        for (DnsEntry ans : additionalResources()) {
            sb.append(ans).append(" ");
        }
        sb.append("], sender=").append(sender);

        return sb.append('}').toString();
    }
}

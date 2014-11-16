/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.dns.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;

/**
 * Answers questions in response to DNS requests.  Used with DnsServerHandler
 * to produce answers.
 */
public interface DnsAnswerProvider {

    /**
     * Called when a DNS query has been received.
     *
     * @param query The DNS query
     * @param ctx The channel context
     * @param callback Callback to call with a DnsResponse
     * @throws Exception if something goes wrong
     */
    void respond(DnsQuery query, ChannelHandlerContext ctx, DnsResponseSender callback) throws Exception;

    public interface DnsResponseSender {

        void withResponse(DnsResponse response) throws Exception;
    }
}

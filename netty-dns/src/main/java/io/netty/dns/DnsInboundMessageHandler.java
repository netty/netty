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
package io.netty.dns;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsResponse;

/**
 * Handles listening for messages for a single DNS server and passes messages to the {@link DnsCallback} class to be
 * linked to their callback.
 */
public class DnsInboundMessageHandler extends SimpleChannelInboundHandler<DnsResponse> {

    /**
     * Called when a new {@link DnsMessage} is received. The callback corresponding to this message is found and
     * finished.
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, DnsResponse response) throws Exception {
        DnsCallback.finish(response);
    }

}

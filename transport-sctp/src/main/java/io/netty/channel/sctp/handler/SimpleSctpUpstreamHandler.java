/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp.handler;

import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.channel.sctp.SctpNotificationEvent;

/**
 * SCTP Upstream Channel Handler with SCTP notification handling.
 */
public class SimpleSctpUpstreamHandler extends SimpleChannelUpstreamHandler {
    
    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent event) throws Exception {
        if (!(event instanceof SctpNotificationEvent)) {
            super.handleUpstream(ctx, event);

        }
        if (event instanceof SctpNotificationEvent) {
            sctpNotificationReceived(ctx, (SctpNotificationEvent) event);
        }
    }

    public void sctpNotificationReceived(ChannelHandlerContext ctx, SctpNotificationEvent event) {
        ctx.sendUpstream(event);
    }
}

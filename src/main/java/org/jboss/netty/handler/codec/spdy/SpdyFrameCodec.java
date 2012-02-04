/*
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;

/**
 * A combination of {@link SpdyFrameDecoder} and {@link SpdyFrameEncoder}.
 * @apiviz.has org.jboss.netty.handler.codec.spdy.SpdyFrameDecoder
 * @apiviz.has org.jboss.netty.handler.codec.spdy.SpdyFrameEncoder
 */
public class SpdyFrameCodec implements ChannelUpstreamHandler,
       ChannelDownstreamHandler {

    private final SpdyFrameDecoder decoder = new SpdyFrameDecoder();
    private final SpdyFrameEncoder encoder = new SpdyFrameEncoder();

    public SpdyFrameCodec() {
    }

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        decoder.handleUpstream(ctx, e);
    }

    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        encoder.handleDownstream(ctx, e);
    }
}

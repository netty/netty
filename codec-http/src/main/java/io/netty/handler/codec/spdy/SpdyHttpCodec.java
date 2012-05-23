/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.channel.ChannelDownstreamHandler;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelUpstreamHandler;

/**
 * A combination of {@link SpdyHttpDecoder} and {@link SpdyHttpEncoder}
 * @apiviz.has io.netty.handler.codec.sdpy.SpdyHttpDecoder
 * @apiviz.has io.netty.handler.codec.spdy.SpdyHttpEncoder
 */
public class SpdyHttpCodec implements ChannelUpstreamHandler, ChannelDownstreamHandler {

    private final SpdyHttpDecoder decoder;
    private final SpdyHttpEncoder encoder;

    /**
     * Creates a new instance with the specified decoder options.
     */
    public SpdyHttpCodec(int version, int maxContentLength) {
        decoder = new SpdyHttpDecoder(version, maxContentLength);
        encoder = new SpdyHttpEncoder(version);
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

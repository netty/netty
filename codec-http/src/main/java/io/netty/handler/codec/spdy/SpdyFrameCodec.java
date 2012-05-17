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
 * A combination of {@link SpdyFrameDecoder} and {@link SpdyFrameEncoder}.
 * @apiviz.has io.netty.handler.codec.spdy.SpdyFrameDecoder
 * @apiviz.has io.netty.handler.codec.spdy.SpdyFrameEncoder
 */
public class SpdyFrameCodec implements ChannelUpstreamHandler,
       ChannelDownstreamHandler {

    private final SpdyFrameDecoder decoder;
    private final SpdyFrameEncoder encoder;

    /**
     * Creates a new instance with the default decoder and encoder options
     * ({@code maxChunkSize (8192)}, {@code maxFrameSize (65536)},
     * {@code maxHeaderSize (16384)}, {@code compressionLevel (6)},
     * {@code windowBits (15)}, and {@code memLevel (8)}).
     */
    public SpdyFrameCodec() {
        this(8192, 65536, 16384, 6, 15, 8);
    }

    /**
     * Creates a new instance with the specified decoder and encoder options.
     */
    public SpdyFrameCodec(
            int maxChunkSize, int maxFrameSize, int maxHeaderSize,
            int compressionLevel, int windowBits, int memLevel) {
        decoder = new SpdyFrameDecoder(maxChunkSize, maxFrameSize, maxHeaderSize);
        encoder = new SpdyFrameEncoder(compressionLevel, windowBits, memLevel);
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

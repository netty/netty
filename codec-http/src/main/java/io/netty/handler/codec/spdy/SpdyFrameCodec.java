/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.CombinedChannelDuplexHandler;


/**
 * A combination of {@link SpdyFrameDecoder} and {@link SpdyFrameEncoder}.
 */
public final class SpdyFrameCodec
        extends CombinedChannelDuplexHandler
        implements ChannelInboundByteHandler, ChannelOutboundMessageHandler<SpdyDataOrControlFrame> {

    /**
     * Creates a new instance with the specified {@code version} and
     * the default decoder and encoder options
     * ({@code maxChunkSize (8192)}, {@code maxHeaderSize (16384)},
     * {@code compressionLevel (6)}, {@code windowBits (15)},
     * and {@code memLevel (8)}).
     */
    public SpdyFrameCodec(int version) {
        this(version, 8192, 16384, 6, 15, 8);
    }

    /**
     * Creates a new instance with the specified decoder and encoder options.
     */
    public SpdyFrameCodec(
            int version, int maxChunkSize, int maxHeaderSize,
            int compressionLevel, int windowBits, int memLevel) {
        super(
                new SpdyFrameDecoder(version, maxChunkSize, maxHeaderSize),
                new SpdyFrameEncoder(version, compressionLevel, windowBits, memLevel));
    }

    private SpdyFrameDecoder decoder() {
        return (SpdyFrameDecoder) stateHandler();
    }

    private SpdyFrameEncoder encoder() {
        return (SpdyFrameEncoder) operationHandler();
    }

    @Override
    public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return decoder().newInboundBuffer(ctx);
    }

    @Override
    public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        decoder().discardInboundReadBytes(ctx);
    }

    @Override
    public MessageBuf<SpdyDataOrControlFrame> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return encoder().newOutboundBuffer(ctx);
    }
}

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
package io.netty.channel.embedded;

import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;


/**
 * Embedded {@link Channel} which operates on bytes
 */
public class EmbeddedByteChannel extends AbstractEmbeddedChannel<ByteBuf> {

    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.BYTE, false);

    /**
     * Create a new instance with the given {@link ChannelHandler}s in the {@link ChannelPipeline}
     */
    public EmbeddedByteChannel(ChannelHandler... handlers) {
        super(Unpooled.buffer(), handlers);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public ByteBuf inboundBuffer() {
        return pipeline().inboundByteBuffer();
    }

    @Override
    public ByteBuf lastOutboundBuffer() {
        return (ByteBuf) lastOutboundBuffer;
    }

    @Override
    public ByteBuf readOutbound() {
        if (!lastOutboundBuffer().isReadable()) {
            return null;
        }
        try {
            return lastOutboundBuffer().readBytes(lastOutboundBuffer().readableBytes());
        } finally {
            lastOutboundBuffer().clear();
        }
    }

    @Override
    protected void writeInbound0(ByteBuf data) {
        inboundBuffer().writeBytes(data);
    }

    @Override
    protected boolean hasReadableOutboundBuffer() {
        return lastOutboundBuffer().isReadable();
    }

    @Override
    protected void doFlushByteBuffer(ByteBuf buf) throws Exception {
        lastOutboundBuffer().writeBytes(buf);
    }
}

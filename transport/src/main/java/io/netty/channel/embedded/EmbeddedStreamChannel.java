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

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelType;

public class EmbeddedStreamChannel extends AbstractEmbeddedChannel {

    public EmbeddedStreamChannel(ChannelHandler... handlers) {
        super(ChannelBuffers.dynamicBuffer(), handlers);
    }

    @Override
    public ChannelType type() {
        return ChannelType.STREAM;
    }

    public ChannelBuffer inboundBuffer() {
        return pipeline().inboundByteBuffer();
    }

    public ChannelBuffer lastOutboundBuffer() {
        return (ChannelBuffer) lastOutboundBuffer;
    }

    public ChannelBuffer readOutbound() {
        if (!lastOutboundBuffer().readable()) {
            return null;
        }
        try {
            return lastOutboundBuffer().readBytes(lastOutboundBuffer().readableBytes());
        } finally {
            lastOutboundBuffer().clear();
        }
    }

    public boolean writeInbound(ChannelBuffer data) {
        inboundBuffer().writeBytes(data);
        pipeline().fireInboundBufferUpdated();
        checkException();
        return lastInboundByteBuffer().readable() || !lastInboundMessageBuffer().isEmpty();
    }

    public boolean writeOutbound(Object msg) {
        write(msg);
        checkException();
        return lastOutboundBuffer().readable();
    }

    public boolean finish() {
        close();
        checkException();
        return lastInboundByteBuffer().readable() || !lastInboundMessageBuffer().isEmpty() ||
               lastOutboundBuffer().readable();
    }

    @Override
    protected void doFlushByteBuffer(ChannelBuffer buf) throws Exception {
        if (!lastOutboundBuffer().readable()) {
            lastOutboundBuffer().discardReadBytes();
        }
        lastOutboundBuffer().writeBytes(buf);
    }
}

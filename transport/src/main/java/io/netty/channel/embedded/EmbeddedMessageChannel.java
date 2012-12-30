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
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;

/**
 * Embedded {@link Channel} which operates on messages which can be of any time.
 */
public class EmbeddedMessageChannel extends AbstractEmbeddedChannel<Object> {

    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.MESSAGE, false);

    /**
     * Create a new instance with the given {@link ChannelHandler}s in the {@link ChannelPipeline}
     */
    public EmbeddedMessageChannel(ChannelHandler... handlers) {
        super(Unpooled.messageBuffer(), handlers);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public MessageBuf<Object> inboundBuffer() {
        return pipeline().inboundMessageBuffer();
    }

    @Override
    @SuppressWarnings("unchecked")
    public MessageBuf<Object> lastOutboundBuffer() {
        return (MessageBuf<Object>) lastOutboundBuffer;
    }

    @Override
    public Object readOutbound() {
        return lastOutboundBuffer().poll();
    }

    @Override
    protected void writeInbound0(Object data) {
        inboundBuffer().add(data);
    }

    @Override
    protected boolean hasReadableOutboundBuffer() {
        return !lastOutboundBuffer().isEmpty();
    }

    @Override
    protected void doFlushMessageBuffer(MessageBuf<Object> buf) throws Exception {
        buf.drainTo(lastOutboundBuffer());
    }
}

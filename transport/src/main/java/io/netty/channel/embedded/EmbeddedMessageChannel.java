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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelBufferType;

import java.util.ArrayDeque;
import java.util.Queue;

public class EmbeddedMessageChannel extends AbstractEmbeddedChannel {

    public EmbeddedMessageChannel(ChannelHandler... handlers) {
        super(new ArrayDeque<Object>(), handlers);
    }

    @Override
    public ChannelBufferType bufferType() {
        return ChannelBufferType.MESSAGE;
    }

    public Queue<Object> inboundBuffer() {
        return pipeline().inboundMessageBuffer();
    }

    @SuppressWarnings("unchecked")
    public Queue<Object> lastOutboundBuffer() {
        return (Queue<Object>) lastOutboundBuffer;
    }

    public Object readOutbound() {
        return lastOutboundBuffer().poll();
    }

    public boolean writeInbound(Object msg) {
        inboundBuffer().add(msg);
        pipeline().fireInboundBufferUpdated();
        checkException();
        return lastInboundByteBuffer().readable() || !lastInboundMessageBuffer().isEmpty();
    }

    public boolean writeOutbound(Object msg) {
        write(msg);
        checkException();
        return !lastOutboundBuffer().isEmpty();
    }

    public boolean finish() {
        close();
        checkException();
        return lastInboundByteBuffer().readable() || !lastInboundMessageBuffer().isEmpty() ||
               !lastOutboundBuffer().isEmpty();
    }

    @Override
    protected void doFlushMessageBuffer(Queue<Object> buf) throws Exception {
        for (;;) {
            Object o = buf.poll();
            if (o == null) {
                break;
            }
            lastOutboundBuffer().add(o);
        }
    }
}

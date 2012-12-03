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
package org.jboss.netty.handler.codec.marshalling;

import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.handler.codec.replay.VoidEnum;

import java.io.ObjectStreamConstants;

/**
 * {@link ReplayingDecoder} which use an {@link Unmarshaller} to read the Object out of the {@link ChannelBuffer}.
 *
 * If you can you should use {@link MarshallingDecoder}.
 *
 *
 *
 */
public class CompatibleMarshallingDecoder extends ReplayingDecoder<VoidEnum> {
    protected final UnmarshallerProvider provider;
    protected final int maxObjectSize;
    private boolean discardingTooLongFrame;

    /**
     * Create a new instance of {@link CompatibleMarshallingDecoder}.
     *
     * @param provider
     *        the {@link UnmarshallerProvider} which is used to obtain the {@link Unmarshaller}
     *        for the {@link Channel}
     * @param maxObjectSize
     *        the maximal size (in bytes) of the {@link Object} to unmarshal. Once the size is
     *        exceeded the {@link Channel} will get closed. Use a a maxObjectSize of
     *        {@link Integer#MAX_VALUE} to disable this. You should only do this if you are sure
     *        that the received Objects will never be big and the sending side are trusted, as
     *        this opens the possibility for a DOS-Attack due an {@link OutOfMemoryError}.
     */
    public CompatibleMarshallingDecoder(UnmarshallerProvider provider, int maxObjectSize) {
        this.provider = provider;
        this.maxObjectSize = maxObjectSize;
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, VoidEnum state) throws Exception {
        if (discardingTooLongFrame) {
            buffer.skipBytes(actualReadableBytes());
            checkpoint();
            return null;
        }

        Unmarshaller unmarshaller = provider.getUnmarshaller(ctx);
        ByteInput input = new ChannelBufferByteInput(buffer);
        if (maxObjectSize != Integer.MAX_VALUE) {
            input = new LimitingByteInput(input, maxObjectSize);
        }
        try {
            unmarshaller.start(input);
            Object obj = unmarshaller.readObject();
            unmarshaller.finish();
            return obj;
        } catch (LimitingByteInput.TooBigObjectException e) {
            discardingTooLongFrame = true;
            throw new TooLongFrameException();
        } finally {
            // Call close in a finally block as the ReplayingDecoder will throw an Error if not enough bytes are
            // readable. This helps to be sure that we do not leak resource
            unmarshaller.close();
        }
    }

    @Override
    protected Object decodeLast(ChannelHandlerContext ctx, Channel channel,
            ChannelBuffer buffer, VoidEnum state)
            throws Exception {
        switch (buffer.readableBytes()) {
        case 0:
            return null;
        case 1:
            // Ignore the last TC_RESET
            if (buffer.getByte(buffer.readerIndex()) == ObjectStreamConstants.TC_RESET) {
                buffer.skipBytes(1);
                return null;
            }
        }

        Object decoded = decode(ctx, channel, buffer, state);
        return decoded;
    }

    /**
     * Calls {@link Channel#close()} if a TooLongFrameException was thrown
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (e.getCause() instanceof TooLongFrameException) {
            e.getChannel().close();
        } else {
            super.exceptionCaught(ctx, e);
        }
    }
}

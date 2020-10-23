/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.marshalling;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.TooLongFrameException;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.Unmarshaller;

import java.io.ObjectStreamConstants;
import java.util.List;

/**
 * {@link ReplayingDecoder} which use an {@link Unmarshaller} to read the Object out of the {@link ByteBuf}.
 *
 * If you can you should use {@link MarshallingDecoder}.
 */
public class CompatibleMarshallingDecoder extends ReplayingDecoder<Void> {
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
     *        exceeded the {@link Channel} will get closed. Use a maxObjectSize of
     *        {@link Integer#MAX_VALUE} to disable this.  You should only do this if you are sure
     *        that the received Objects will never be big and the sending side are trusted, as this
     *        opens the possibility for a DOS-Attack due an {@link OutOfMemoryError}.
     */
    public CompatibleMarshallingDecoder(UnmarshallerProvider provider, int maxObjectSize) {
        this.provider = provider;
        this.maxObjectSize = maxObjectSize;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        if (discardingTooLongFrame) {
            buffer.skipBytes(actualReadableBytes());
            checkpoint();
            return;
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
            out.add(obj);
        } catch (LimitingByteInput.TooBigObjectException ignored) {
            discardingTooLongFrame = true;
            throw new TooLongFrameException();
        } finally {
            // Call close in a finally block as the ReplayingDecoder will throw an Error if not enough bytes are
            // readable. This helps to be sure that we do not leak resource
            unmarshaller.close();
        }
    }

    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        switch (buffer.readableBytes()) {
        case 0:
            return;
        case 1:
            // Ignore the last TC_RESET
            if (buffer.getByte(buffer.readerIndex()) == ObjectStreamConstants.TC_RESET) {
                buffer.skipBytes(1);
                return;
            }
        }

        decode(ctx, buffer, out);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof TooLongFrameException) {
            ctx.close();
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}

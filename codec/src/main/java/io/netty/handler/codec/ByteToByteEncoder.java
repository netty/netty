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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundByteHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
import io.netty.channel.IncompleteFlushException;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * {@link ChannelOutboundByteHandlerAdapter} which encodes bytes in a stream-like fashion from one {@link ByteBuf} to an
 * other.
 *
 * This kind of decoder is often useful for doing on-the-fly processing like i.e. compression.
 *
 * But you can also do other things with it. For example here is an implementation which reads {@link Integer}s from
 * the input {@link ByteBuf} and square them before write them to the output {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareEncoder extends {@link ByteToByteEncoder} {
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in, {@link ByteBuf} out)
 *                 throws {@link Exception} {
 *             if (in.readableBytes() < 4) {
 *                 return;
 *             }
 *             int value = in.readInt();
 *             out.writeInt(value * value);
 *         }
 *     }
 * </pre>
 */
public abstract class ByteToByteEncoder extends ChannelOutboundByteHandlerAdapter {

    @Override
    protected void flush(ChannelHandlerContext ctx, ByteBuf in, ChannelPromise promise) throws Exception {
        ByteBuf out = ctx.nextOutboundByteBuffer();
        boolean encoded = false;

        try {
            while (in.isReadable()) {
                int oldInSize = in.readableBytes();
                encode(ctx, in, out);
                encoded = true;
                if (oldInSize == in.readableBytes()) {
                    break;
                }
            }
        } catch (Throwable t) {
            if (!(t instanceof CodecException)) {
                t = new EncoderException(t);
            }
            if (encoded) {
                t = new IncompleteFlushException("unable to encode all bytes", t);
            }
            in.discardSomeReadBytes();
            promise.setFailure(t);
            return;
        }

        ctx.flush(promise);
    }

    @Override
    public void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) throws Exception {
        long written = 0;
        try {
            for (;;) {
                long localWritten = region.transferTo(new BufferChannel(ctx.outboundByteBuffer()), written);
                if (localWritten == -1) {
                    checkEOF(region, written);
                    flush(ctx, promise);
                    break;
                }
                written += localWritten;
                if (written >= region.count()) {
                    flush(ctx, promise);
                    break;
                }
            }
        } catch (IOException e) {
            promise.setFailure(new EncoderException(e));
        } finally {
            region.release();
        }
    }

    private static void checkEOF(FileRegion region, long writtenBytes) throws IOException {
        if (writtenBytes < region.count()) {
            throw new EOFException("Expected to be able to write "
                    + region.count() + " bytes, but only wrote "
                    + writtenBytes);
        }
    }

    private static final class BufferChannel implements WritableByteChannel {
        private final ByteBuf buffer;

        BufferChannel(ByteBuf buffer) {
            this.buffer = buffer;
        }
        @Override
        public int write(ByteBuffer src) {
            int bytes = src.remaining();
            buffer.writeBytes(src);
            return bytes;
        }

        @Override
        public boolean isOpen() {
            return buffer.refCnt() > 0;
        }

        @Override
        public void close() {
            // NOOP
        }
    }

    /**
     * Encodes the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read anymore or till nothing was read from the input {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToByteDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link ByteBuf} to which the decoded data will be written
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception;
}

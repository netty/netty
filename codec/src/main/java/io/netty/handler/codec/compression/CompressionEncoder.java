/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.ByteToByteEncoder;
import io.netty.handler.codec.EncoderException;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Special {@link ByteToByteEncoder} which intercepts
 * {@link #sendFile(ChannelHandlerContext, FileRegion, ChannelPromise)} operations and transfer the contents
 * of the {@link FileRegion} in the outbound buffer of the encoder and call
 * {@link #flush(ChannelHandlerContext, ChannelPromise)}. This way even the content of the send {@link FileRegion}
 * is compressed.
 *
 */
public abstract class CompressionEncoder extends ByteToByteEncoder {

    private WritableByteChannel bufferChannel;

    @Override
    public final void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) throws Exception {
        if (bufferChannel == null) {
            bufferChannel = new BufferChannel(ctx.outboundByteBuffer());
        }
        long written = 0;
        try {
            for (;;) {
                long localWritten = region.transferTo(bufferChannel, written);
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
}

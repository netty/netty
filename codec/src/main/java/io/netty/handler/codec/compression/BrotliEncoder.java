/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.compression;

import com.aayushatharva.brotli4j.encoder.BrotliEncoderChannel;
import com.aayushatharva.brotli4j.encoder.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;

/**
 * Compress a {@link ByteBuf} with the Brotli compression.
 * <p>
 * See <a href="https://github.com/google/brotli">brotli</a>.
 */
@ChannelHandler.Sharable
public final class BrotliEncoder extends MessageToByteEncoder<ByteBuf> {

    private static final AttributeKey<Writer> ATTR = AttributeKey.valueOf("BrotliEncoderWriter");

    private final Encoder.Parameters parameters;
    private final boolean isSharable;
    private Writer writer;

    /**
     * Create a new {@link BrotliEncoder} Instance with {@link BrotliOptions#DEFAULT}
     * and {@link #isSharable()} set to {@code true}
     */
    public BrotliEncoder() {
        this(BrotliOptions.DEFAULT);
    }

    /**
     * Create a new {@link BrotliEncoder} Instance
     *
     * @param brotliOptions {@link BrotliOptions} to use and
     *                      {@link #isSharable()} set to {@code true}
     */
    public BrotliEncoder(BrotliOptions brotliOptions) {
        this(brotliOptions.parameters());
    }

    /**
     * Create a new {@link BrotliEncoder} Instance
     * and {@link #isSharable()} set to {@code true}
     *
     * @param parameters {@link Encoder.Parameters} to use
     */
    public BrotliEncoder(Encoder.Parameters parameters) {
        this(parameters, true);
    }

    /**
     * <p>
     * Create a new {@link BrotliEncoder} Instance and specify
     * whether this instance will be shared with multiple pipelines or not.
     * </p>
     *
     * If {@link #isSharable()} is true then on {@link #handlerAdded(ChannelHandlerContext)} call,
     * a new {@link Writer} will create, and it will be mapped using {@link Channel#attr(AttributeKey)}
     * so {@link BrotliEncoder} can be shared with multiple pipelines. This works fine but there on every
     * {@link #encode(ChannelHandlerContext, ByteBuf, ByteBuf)} call, we have to get the {@link Writer} associated
     * with the appropriate channel. And this will add a overhead. So it is recommended to set {@link #isSharable()}
     * to {@code false} and create new {@link BrotliEncoder} instance for every pipeline.
     *
     * @param parameters {@link Encoder.Parameters} to use
     * @param isSharable Set to {@code true} if this instance is shared else set to {@code false}
     */
    public BrotliEncoder(Encoder.Parameters parameters, boolean isSharable) {
        this.parameters = ObjectUtil.checkNotNull(parameters, "Parameters");
        this.isSharable = isSharable;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Writer writer = new Writer(parameters, ctx);
        if (isSharable) {
            ctx.channel().attr(ATTR).set(writer);
        } else {
            this.writer = writer;
        }
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        finish(ctx);
        super.handlerRemoved(ctx);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        // NO-OP
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) throws Exception {
        if (!msg.isReadable()) {
            return Unpooled.EMPTY_BUFFER;
        }

        Writer writer;
        if (isSharable) {
            writer = ctx.channel().attr(ATTR).get();
        } else {
            writer = this.writer;
        }

        // If Writer is 'null' then Writer is not open.
        if (writer == null) {
            return Unpooled.EMPTY_BUFFER;
        } else {
            writer.encode(msg, preferDirect);
            return writer.writableBuffer;
        }
    }

    @Override
    public boolean isSharable() {
        return isSharable;
    }

    /**
     * Finish the encoding, close streams and write final {@link ByteBuf} to the channel.
     *
     * @param ctx {@link ChannelHandlerContext} which we want to close
     * @throws IOException If an error occurred during closure
     */
    public void finish(ChannelHandlerContext ctx) throws IOException {
        finishEncode(ctx, ctx.newPromise());
    }

    private ChannelFuture finishEncode(ChannelHandlerContext ctx, ChannelPromise promise) throws IOException {
        Writer writer;

        if (isSharable) {
            writer = ctx.channel().attr(ATTR).getAndSet(null);
        } else {
            writer = this.writer;
        }

        if (writer != null) {
            writer.close();
            this.writer = null;
        }
        return promise;
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        ChannelFuture f = finishEncode(ctx, ctx.newPromise());
        EncoderUtil.closeAfterFinishEncode(ctx, f, promise);
    }

    /**
     * {@link Writer} is the implementation of {@link WritableByteChannel} which encodes
     * Brotli data and stores it into {@link ByteBuf}.
     */
    private static final class Writer implements WritableByteChannel {

        private ByteBuf writableBuffer;
        private final BrotliEncoderChannel brotliEncoderChannel;
        private final ChannelHandlerContext ctx;
        private boolean isClosed;

        private Writer(Encoder.Parameters parameters, ChannelHandlerContext ctx) throws IOException {
            brotliEncoderChannel = new BrotliEncoderChannel(this, parameters);
            this.ctx = ctx;
        }

        private void encode(ByteBuf msg, boolean preferDirect) throws Exception {
            try {
                allocate(preferDirect);

                // Compress data and flush it into Buffer.
                //
                // As soon as we call flush, Encoder will be triggered to write encoded
                // data into WritableByteChannel.
                //
                // A race condition will not arise because one flush call to encoder will result
                // in only 1 call at `write(ByteBuffer)`.
                ByteBuffer nioBuffer = CompressionUtil.safeReadableNioBuffer(msg);
                int position = nioBuffer.position();
                brotliEncoderChannel.write(nioBuffer);
                msg.skipBytes(nioBuffer.position() - position);
                brotliEncoderChannel.flush();
            } catch (Exception e) {
                ReferenceCountUtil.release(msg);
                throw e;
            }
        }

        private void allocate(boolean preferDirect) {
            if (preferDirect) {
                writableBuffer = ctx.alloc().ioBuffer();
            } else {
                writableBuffer = ctx.alloc().buffer();
            }
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (!isOpen()) {
                throw new ClosedChannelException();
            }

            return writableBuffer.writeBytes(src).readableBytes();
        }

        @Override
        public boolean isOpen() {
            return !isClosed;
        }

        @Override
        public void close() {
            final ChannelPromise promise = ctx.newPromise();

            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        finish(promise);
                    } catch (IOException ex) {
                        promise.setFailure(new IllegalStateException("Failed to finish encoding", ex));
                    }
                }
            });
        }

        public void finish(final ChannelPromise promise) throws IOException {
            if (!isClosed) {
                // Allocate a buffer and write last pending data.
                allocate(true);

                try {
                    brotliEncoderChannel.close();
                    isClosed = true;
                } catch (Exception ex) {
                    promise.setFailure(ex);

                    // Since we have already allocated Buffer for close operation,
                    // we will release that buffer to prevent memory leak.
                    ReferenceCountUtil.release(writableBuffer);
                    return;
                }

                ctx.writeAndFlush(writableBuffer, promise);
            }
        }
    }
}

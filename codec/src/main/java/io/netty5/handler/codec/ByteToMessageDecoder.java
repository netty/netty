/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundInvoker;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.internal.DelegatingChannelHandlerContext;
import io.netty5.util.Send;
import io.netty5.util.internal.StringUtil;

import java.util.Arrays;

import static io.netty5.util.internal.MathUtil.safeFindNextPositivePowerOfTwo;
import static java.util.Objects.requireNonNull;

/**
 * {@link ChannelHandler} which decodes bytes in a stream-like fashion from one {@link Buffer} to an
 * other Message type.
 *
 * For example here is an implementation which reads all readable bytes from
 * the input {@link Buffer}, creates a new {@link Buffer} and forward it to the next {@link ChannelHandler}
 * in the {@link ChannelPipeline}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToMessageDecoder} {
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link Buffer} in)
 *                 throws {@link Exception} {
 *             ctx.fireChannelRead(in.readBytes(in.readableBytes()));
 *         }
 *     }
 * </pre>
 *
 * <h3>Frame detection</h3>
 * <p>
 * Generally frame detection should be handled earlier in the pipeline by adding a
 * {@link DelimiterBasedFrameDecoder}, {@link FixedLengthFrameDecoder}, {@link LengthFieldBasedFrameDecoder},
 * or {@link LineBasedFrameDecoder}.
 * <p>
 * If a custom frame decoder is required, then one needs to be careful when implementing
 * one with {@link ByteToMessageDecoder}. Ensure there are enough bytes in the buffer for a
 * complete frame by checking {@link Buffer#readableBytes()}. If there are not enough bytes
 * for a complete frame, return without modifying the reader index to allow more bytes to arrive.
 * <p>
 * To check for complete frames without modifying the reader index, use methods like {@link Buffer#getInt(int)}.
 * One <strong>MUST</strong> use the reader index when using methods like {@link Buffer#getInt(int)}.
 * For example calling <tt>in.getInt(0)</tt> is assuming the frame starts at the beginning of the buffer, which
 * is not always the case. Use <tt>in.getInt(in.readerIndex())</tt> instead.
 * <h3>Pitfalls</h3>
 * <p>
 * Be aware that sub-classes of {@link ByteToMessageDecoder} <strong>MUST NOT</strong>
 * annotated with {@link @Sharable}.
 */
public abstract class ByteToMessageDecoder extends ChannelHandlerAdapter {

    /**
     * Cumulate {@link Buffer}s by merge them into one {@link Buffer}'s, using memory copies.
     */
    public static final Cumulator MERGE_CUMULATOR = new MergeCumulator();

    /**
     * Cumulate {@link Buffer}s by add them to a {@link CompositeBuffer} and so do no memory copy whenever possible.
     * Be aware that {@link CompositeBuffer} use a more complex indexing implementation so depending on your use-case
     * and the decoder implementation this may be slower than just use the {@link #MERGE_CUMULATOR}.
     */
    public static final Cumulator COMPOSITE_CUMULATOR = new CompositeBufferCumulator();

    private final int discardAfterReads = 16;
    private final Cumulator cumulator;

    private Buffer cumulation;
    private boolean singleDecode;
    private boolean first;
    /**
     * This flag is used to determine if we need to call {@link ChannelOutboundInvoker#read(ReadBufferAllocator)}
     * to consume more data when {@link ChannelOption#AUTO_READ} is {@code false}.
     */
    private boolean firedChannelRead;
    private int numReads;
    private ByteToMessageDecoderContext context;

    protected ByteToMessageDecoder() {
        this(MERGE_CUMULATOR);
    }

    protected ByteToMessageDecoder(Cumulator cumulator) {
        this.cumulator = requireNonNull(cumulator, "cumulator");
    }

    @Override
    public final boolean isSharable() {
        // Can't be sharable as we keep state.
        return false;
    }

    /**
     * If set then only one message is decoded on each {@link #channelRead(ChannelHandlerContext, Object)}
     * call. This may be useful if you need to do some protocol upgrade and want to make sure nothing is mixed up.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public void setSingleDecode(boolean singleDecode) {
        this.singleDecode = singleDecode;
    }

    /**
     * If {@code true} then only one message is decoded on each
     * {@link #channelRead(ChannelHandlerContext, Object)} call.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public boolean isSingleDecode() {
        return singleDecode;
    }

    /**
     * Returns the actual number of readable bytes in the internal cumulative
     * buffer of this decoder. You usually do not need to rely on this value
     * to write a decoder. Use it only when you must use it at your own risk.
     * This method is a shortcut to {@link #internalBuffer() internalBuffer().readableBytes()}.
     */
    protected int actualReadableBytes() {
        return internalBuffer().readableBytes();
    }

    /**
     * Returns the internal cumulative buffer of this decoder, if exists, else {@code null}. You usually
     * do not need to access the internal buffer directly to write a decoder.
     * Use it only when you must use it at your own risk.
     *
     * @return Internal {@link Buffer} if exists, else {@code null}.
     */
    protected Buffer internalBuffer() {
        return cumulation;
    }

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        context = new ByteToMessageDecoderContext(ctx);
        handlerAdded0(context);
    }

    protected void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Buffer buf = cumulation;
        if (buf != null) {
            // Directly set this to null so we are sure we not access it in any other method here anymore.
            cumulation = null;
            numReads = 0;
            int readable = buf.readableBytes();
            if (readable > 0) {
                ctx.fireChannelRead(buf);
                ctx.fireChannelReadComplete();
            } else {
                buf.close();
            }
        }
        handlerRemoved0(context);
    }

    /**
     * Gets called after the {@link ByteToMessageDecoder} was removed from the actual context and it doesn't
     * handle events anymore.
     */
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception { }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Buffer) {
            try {
                Buffer data = (Buffer) msg;
                first = cumulation == null;
                if (first) {
                    cumulation = data;
                } else {
                    cumulation = cumulator.cumulate(ctx.bufferAllocator(), cumulation, data);
                }
                assert context.delegatingCtx() == ctx || ctx == context;

                callDecode(context, cumulation);
            } catch (DecoderException e) {
                throw e;
            } catch (Exception e) {
                throw new DecoderException(e);
            } finally {
                if (cumulation != null && cumulation.readableBytes() == 0) {
                    numReads = 0;
                    if (cumulation.isAccessible()) {
                        cumulation.close();
                    }
                    cumulation = null;
                } else if (++numReads >= discardAfterReads) {
                    // We did enough reads already try to discard some bytes so we not risk to see a OOME.
                    // See https://github.com/netty/netty/issues/4275
                    numReads = 0;
                    discardSomeReadBytes();
                }

                firedChannelRead |= context.fireChannelReadCallCount() > 0;
                context.reset();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        numReads = 0;
        discardSomeReadBytes();
        if (!firedChannelRead && !ctx.channel().getOption(ChannelOption.AUTO_READ)) {
            ctx.read();
        }
        firedChannelRead = false;
        ctx.fireChannelReadComplete();
    }

    protected final void discardSomeReadBytes() {
        if (cumulation != null && !first) {
            // discard some bytes if possible to make more room in the buffer.
            cumulator.discardSomeReadBytes(cumulation);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        assert context.delegatingCtx() == ctx || ctx == context;
        channelInputClosed(context, true);
    }

    @Override
    public void channelShutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) throws Exception {
        ctx.fireChannelShutdown(direction);
        if (direction == ChannelShutdownDirection.Inbound) {
            // The decodeLast method is invoked when a channelInactive event is encountered.
            // This method is responsible for ending requests in some situations and must be called
            // when the input has been shutdown.
            assert context.delegatingCtx() == ctx || ctx == context;
            channelInputClosed(context, false);
        }
    }

    private void channelInputClosed(ByteToMessageDecoderContext ctx, boolean callChannelInactive) {
        try {
            channelInputClosed(ctx);
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            if (cumulation != null) {
                cumulation.close();
                cumulation = null;
            }
            if (ctx.fireChannelReadCallCount() > 0) {
                ctx.reset();
                // Something was read, call fireChannelReadComplete()
                ctx.fireChannelReadComplete();
            }
            if (callChannelInactive) {
                ctx.fireChannelInactive();
            }
        }
    }

    /**
     * Called when the input of the channel was closed which may be because it changed to inactive or because of
     * shutdown.
     */
    void channelInputClosed(ByteToMessageDecoderContext ctx) throws Exception {
        if (cumulation != null) {
            callDecode(ctx, cumulation);
            // If callDecode(...) removed the handle from the pipeline we should not call decodeLast(...) as this would
            // be unexpected.
            if (!ctx.isRemoved()) {
                // Use Unpooled.EMPTY_BUFFER if cumulation become null after calling callDecode(...).
                // See https://github.com/netty/netty/issues/10802.
                Buffer buffer = cumulation == null ? ctx.bufferAllocator().allocate(0) : cumulation;
                decodeLast(ctx, buffer);
            }
        } else {
            decodeLast(ctx, ctx.bufferAllocator().allocate(0));
        }
    }

    /**
     * Called once data should be decoded from the given {@link Buffer}. This method will call
     * {@link #decode(ChannelHandlerContext, Buffer)} as long as decoding should take place.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in  the {@link Buffer} from which to read data
     */
    void callDecode(ByteToMessageDecoderContext ctx, Buffer in) {
        try {
            while (in.readableBytes() > 0 && !ctx.isRemoved()) {

                int oldInputLength = in.readableBytes();
                int numReadCalled = ctx.fireChannelReadCallCount();
                decodeRemovalReentryProtection(ctx, in);

                // Check if this handler was removed before continuing the loop.
                // If it was removed, it is not safe to continue to operate on the buffer.
                //
                // See https://github.com/netty/netty/issues/1664
                if (ctx.isRemoved()) {
                    break;
                }

                if (numReadCalled == ctx.fireChannelReadCallCount()) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }

                if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                                    ".decode() did not read anything but decoded a message.");
                }

                if (isSingleDecode()) {
                    break;
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception cause) {
            throw new DecoderException(cause);
        }
    }

    /**
     * Decode the from one {@link Buffer} to another. This method will be called till either the input
     * {@link Buffer} has nothing to read when return from this method or till nothing was read from the input
     * {@link Buffer}.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in  the {@link Buffer} from which to read data
     * @throws Exception is thrown if an error occurs
     */
    protected abstract void decode(ChannelHandlerContext ctx, Buffer in) throws Exception;

    /**
     * Decode the from one {@link Buffer} to an other. This method will be called till either the input
     * {@link Buffer} has nothing to read when return from this method or till nothing was read from the input
     * {@link Buffer}.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in the {@link Buffer} from which to read data
     * @throws Exception is thrown if an error occurs
     */
    final void decodeRemovalReentryProtection(ChannelHandlerContext ctx, Buffer in)
            throws Exception {
        decode(ctx, in);
    }

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, Buffer)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected void decodeLast(ChannelHandlerContext ctx, Buffer in) throws Exception {
        if (in.readableBytes() > 0) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decodeRemovalReentryProtection(ctx, in);
        }
    }

    /**
     * Cumulate {@link Buffer}s.
     */
    public interface Cumulator {
        /**
         * Cumulate the given {@link Buffer}s and return the {@link Buffer} that holds the cumulated bytes.
         * The implementation is responsible to correctly handle the life-cycle of the given {@link Buffer}s and so
         * call {@link Buffer#close()} if a {@link Buffer} is fully consumed.
         */
        Buffer cumulate(BufferAllocator alloc, Buffer cumulation, Buffer in);

        /**
         * Consume the given buffer and return a new buffer with the same readable data, but where any data before the
         * read offset may have been removed.
         * The returned buffer may be the same buffer instance as the buffer passed in.
         *
         * @param cumulation The buffer we wish to trim already processed bytes from.
         * @return A buffer where the bytes before the reader-offset have been removed.
         */
        Buffer discardSomeReadBytes(Buffer cumulation);
    }

    // Package private so we can also make use of it in ReplayingDecoder.
    static final class ByteToMessageDecoderContext extends DelegatingChannelHandlerContext {
        private int fireChannelReadCalled;

        private ByteToMessageDecoderContext(ChannelHandlerContext ctx) {
            super(ctx);
        }

        void reset() {
            fireChannelReadCalled = 0;
        }

        int fireChannelReadCallCount() {
            return fireChannelReadCalled;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            fireChannelReadCalled ++;
            super.fireChannelRead(msg);
            return this;
        }
    }

    private static final class CompositeBufferCumulator implements Cumulator {
        @Override
        public Buffer cumulate(BufferAllocator alloc, Buffer cumulation, Buffer in) {
            if (cumulation.readableBytes() == 0) {
                cumulation.close();
                return in;
            }
            try (in) {
                if (in.readableBytes() == 0) {
                    return cumulation;
                }
                if (cumulation.readOnly()) {
                    Buffer tmp = cumulation.copy();
                    cumulation.close();
                    cumulation = tmp;
                }
                if (CompositeBuffer.isComposite(cumulation)) {
                    CompositeBuffer composite = (CompositeBuffer) cumulation;
                    composite.extendWith(prepareInForCompose(in));
                    return composite;
                }
                return alloc.compose(Arrays.asList(cumulation.send(), prepareInForCompose(in)));
            }
        }

        private static Send<Buffer> prepareInForCompose(Buffer in) {
            return in.readOnly() ? in.copy().send() : in.send();
        }

        @Override
        public Buffer discardSomeReadBytes(Buffer cumulation) {
            // Compact is slow on composite buffers, and we also need to avoid leaving any writable space at the end.
            // Using readSplit(0), we grab zero readable bytes in the split-off buffer, but all the already-read
            // bytes get cut off from the cumulation buffer.
            cumulation.readSplit(0).close();
            return cumulation;
        }

        @Override
        public String toString() {
            return "CompositeBufferCumulator";
        }
    }

    private static final class MergeCumulator implements Cumulator {
        @Override
        public Buffer cumulate(BufferAllocator alloc, Buffer cumulation, Buffer in) {
            if (cumulation.readableBytes() == 0) {
                // If cumulation is empty and input buffer is contiguous, use it directly
                cumulation.close();
                return in;
            }
            // We must close input Buffer in all cases as otherwise it may produce a leak if writeBytes(...) throw
            // for whatever close (for example because of OutOfMemoryError)
            try (in) {
                final int required = in.readableBytes();
                if (required > cumulation.writableBytes() || cumulation.readOnly()) {
                    return expandCumulationAndWrite(alloc, cumulation, in);
                }
                cumulation.writeBytes(in);
                return cumulation;
            }
        }

        @Override
        public Buffer discardSomeReadBytes(Buffer cumulation) {
            if (cumulation.readerOffset() > cumulation.writableBytes()) {
                cumulation.compact();
            }
            return cumulation;
        }

        private static Buffer expandCumulationAndWrite(BufferAllocator alloc, Buffer oldCumulation, Buffer in) {
            final int newSize = safeFindNextPositivePowerOfTwo(oldCumulation.readableBytes() + in.readableBytes());
            Buffer newCumulation = oldCumulation.readOnly() ? alloc.allocate(newSize) :
                    oldCumulation.ensureWritable(newSize);
            try {
                if (newCumulation != oldCumulation) {
                    newCumulation.writeBytes(oldCumulation);
                }
                newCumulation.writeBytes(in);
                return newCumulation;
            } finally {
                if (newCumulation != oldCumulation) {
                    oldCumulation.close();
                }
            }
        }

        @Override
        public String toString() {
            return "MergeCumulator";
        }
    }
}

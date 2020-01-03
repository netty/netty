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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * {@link ChannelInboundHandlerAdapter} which decodes bytes in a stream-like fashion from one {@link ByteBuf} to an
 * other Message type.
 *
 * For example here is an implementation which reads all readable bytes from
 * the input {@link ByteBuf} and create a new {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToMessageDecoder} {
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(in.readBytes(in.readableBytes()));
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
 * complete frame by checking {@link ByteBuf#readableBytes()}. If there are not enough bytes
 * for a complete frame, return without modifying the reader index to allow more bytes to arrive.
 * <p>
 * To check for complete frames without modifying the reader index, use methods like {@link ByteBuf#getInt(int)}.
 * One <strong>MUST</strong> use the reader index when using methods like {@link ByteBuf#getInt(int)}.
 * For example calling <tt>in.getInt(0)</tt> is assuming the frame starts at the beginning of the buffer, which
 * is not always the case. Use <tt>in.getInt(in.readerIndex())</tt> instead.
 * <h3>Pitfalls</h3>
 * <p>
 * Be aware that sub-classes of {@link ByteToMessageDecoder} <strong>MUST NOT</strong>
 * annotated with {@link @Sharable}.
 * <p>
 * Some methods such as {@link ByteBuf#readBytes(int)} will cause a memory leak if the returned buffer
 * is not released or added to the <tt>out</tt> {@link List}. Use derived buffers like {@link ByteBuf#readSlice(int)}
 * to avoid leaking memory.
 */
public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {

    /**
     * Cumulate {@link ByteBuf}s by merge them into one {@link ByteBuf}'s, using memory copies.
     */
    public static final Cumulator MERGE_CUMULATOR = new Cumulator() {
        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            if (!cumulation.isReadable() && in.isContiguous()) {
                // If cumulation is empty and input buffer is contiguous, use it directly
                cumulation.release();
                return in;
            }
            ByteBuf toRelease = null;
            try {
                final int required = in.readableBytes();
                if (required > cumulation.maxWritableBytes() ||
                        (required > cumulation.maxFastWritableBytes() && cumulation.refCnt() > 1) ||
                        cumulation.isReadOnly()) {
                    // Expand cumulation (by replacing it) under the following conditions:
                    // - cumulation cannot be resized to accommodate the additional data
                    // - cumulation can be expanded with a reallocation operation to accommodate but the buffer is
                    //   assumed to be shared (e.g. refCnt() > 1) and the reallocation may not be safe.
                    ByteBuf newCumulation = alloc.buffer(alloc.calculateNewCapacity(
                            cumulation.readableBytes() + required, MAX_VALUE));
                    toRelease = newCumulation;
                    newCumulation.writeBytes(cumulation).writeBytes(in);
                    toRelease = cumulation;
                    return newCumulation;
                }
                cumulation.writeBytes(in, in.readerIndex(), required);
                in.readerIndex(in.writerIndex());
                return cumulation;
            } finally {
                // We must release in in all cases as otherwise it may produce a leak if writeBytes(...) throw
                // for whatever release (for example because of OutOfMemoryError)
                in.release();
                ReferenceCountUtil.release(toRelease);
            }
        }
    };

    /**
     * Cumulate {@link ByteBuf}s by add them to a {@link CompositeByteBuf} and so do no memory copy whenever possible.
     * Be aware that {@link CompositeByteBuf} use a more complex indexing implementation so depending on your use-case
     * and the decoder implementation this may be slower then just use the {@link #MERGE_CUMULATOR}.
     */
    public static final Cumulator COMPOSITE_CUMULATOR = new Cumulator() {
        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            if (!cumulation.isReadable()) {
                cumulation.release();
                return in;
            }
            CompositeByteBuf composite = null;
            try {
                if (cumulation instanceof CompositeByteBuf && cumulation.refCnt() == 1) {
                    composite = (CompositeByteBuf) cumulation;
                    // Writer index must equal capacity if we are going to "write"
                    // new components to the end
                    if (composite.writerIndex() != composite.capacity()) {
                        composite.capacity(composite.writerIndex());
                    }
                } else {
                    composite = alloc.compositeBuffer(Integer.MAX_VALUE).addFlattenedComponents(true, cumulation);
                }
                composite.addFlattenedComponents(true, in);
                in = null;
                return composite;
            } finally {
                if (in != null) {
                    // We must release if the ownership was not transferred as otherwise it may produce a leak
                    in.release();
                    // Also release any new buffer allocated if we're not returning it
                    if (composite != null && composite != cumulation) {
                        composite.release();
                    }
                }
            }
        }
    };

    private static final byte STATE_INIT = 0;
    private static final byte STATE_CALLING_CHILD_DECODE = 1;
    private static final byte STATE_HANDLER_REMOVED_PENDING = 2;

    private ByteBuf cumulation;
    // This is set transiently only during the call to channelRead(...)
    // to support the removal re-entry case, and is always null outside of this
    private ByteBuf input;
    private Cumulator cumulator = MERGE_CUMULATOR;
    private boolean singleDecode;
    private boolean first;

    private int minRequired = 1;
    private int maxRequired = Integer.MAX_VALUE;

    //TODO how best to expose this option TBD
    private boolean neverRetainInput;

    /**
     * This flag is used to determine if we need to call {@link ChannelHandlerContext#read()} to consume more data
     * when {@link ChannelConfig#isAutoRead()} is {@code false}.
     */
    private boolean firedChannelRead;

    /**
     * A bitmask where the bits are defined as
     * <ul>
     *     <li>{@link #STATE_INIT}</li>
     *     <li>{@link #STATE_CALLING_CHILD_DECODE}</li>
     *     <li>{@link #STATE_HANDLER_REMOVED_PENDING}</li>
     * </ul>
     */
    private byte decodeState = STATE_INIT;
    private int discardAfterReads = 16;
    private int numReads;

    protected ByteToMessageDecoder() {
        ensureNotSharable();
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
     * Set the {@link Cumulator} to use for cumulate the received {@link ByteBuf}s.
     */
    public void setCumulator(Cumulator cumulator) {
        this.cumulator = ObjectUtil.checkNotNull(cumulator, "cumulator");
    }

    /**
     * Set the number of reads after which {@link ByteBuf#discardSomeReadBytes()} are called and so free up memory.
     * The default is {@code 16}.
     */
    public void setDiscardAfterReads(int discardAfterReads) {
        checkPositive(discardAfterReads, "discardAfterReads");
        this.discardAfterReads = discardAfterReads;
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
     * Returns the internal cumulative buffer of this decoder. You usually
     * do not need to access the internal buffer directly to write a decoder.
     * Use it only when you must use it at your own risk.
     */
    protected ByteBuf internalBuffer() {
        if (cumulation != null) {
            return cumulation;
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (decodeState == STATE_CALLING_CHILD_DECODE) {
            decodeState = STATE_HANDLER_REMOVED_PENDING;
            return;
        }
        ByteBuf buf = cumulation;
        if (buf != null) {
            // Directly set this to null so we are sure we not access it in any other method here anymore.
            cumulation = null;
            numReads = 0;
            boolean readFired = false;
            if (buf.isReadable()) {
                ctx.fireChannelRead(buf);
                readFired = true;
            } else {
                buf.release();
            }
            buf = input;
            input = null;
            if (buf != null) {
                assert buf.isReadable();
                ctx.fireChannelRead(buf);
                readFired = true;
            }
            if (readFired) {
                ctx.fireChannelReadComplete();
            }
        }
        handlerRemoved0(ctx);
    }

    /**
     * Gets called after the {@link ByteToMessageDecoder} was removed from the actual context and it doesn't handle
     * events anymore.
     */
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception { }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            CodecOutputList out = CodecOutputList.newInstance();
            try {
                ByteBuf in = (ByteBuf) msg;
                if (cumulator == MERGE_CUMULATOR) {
                    mergeDecode(ctx, in, out);
                } else {
                    nonMergeDecode(ctx, in, out);
                }
            } catch (DecoderException e) {
                throw e;
            } catch (Exception e) {
                throw new DecoderException(e);
            } finally {
                int size = out.size();
                firedChannelRead |= out.insertSinceRecycled();
                fireChannelRead(ctx, out, size);
                out.recycle();
            }
        } else {
            ctx.fireChannelRead(msg); // !(msg instanceof ByteBuf)
        }
    }

    private void mergeDecode(ChannelHandlerContext ctx, final ByteBuf in, CodecOutputList out) {
        try {
            if (cumulation != null) {
                input = in;
            } else if (in.isContiguous()) {
                cumulation = in;
            } else {
                input = in;
                setCumulationFromNonContiguousInput(ctx);
            }
            for (;;) {
                // If <= targetReadable bytes are remaining in cumulation post-decode then
                // it can be discarded (and replaced with re-wound input buffer if applicable)
                int targetReadable = 0;
                final int existingBytes = cumulation.readableBytes();
                if (existingBytes < minRequired) {
                    if (input == null) {
                        break; // need more data to make progress
                    }
                    targetReadable = moveSomeInputToCumulation(ctx, existingBytes);
                    if (targetReadable == -1) {
                        break; // need more data to make progress
                    }
                }

                boolean finished;
                try {
                    callDecode(ctx, cumulation, out);
                } finally {
                    if (cumulation == null) {
                        // This could happen if handler was removed during the call to decode
                        finished = true;
                    } else {
                        int readableAfter = cumulation.readableBytes();
                        if (readableAfter <= targetReadable) {
                            // target number of remaining bytes in cumulation reached
                            finished = handleReadTargetAttained(ctx, readableAfter);
                        } else {
                            // finished if no more input
                            finished = input == null;
                        }
                    }
                }

                if (finished) {
                    break;
                }
                if (isSingleDecode() && out.insertSinceRecycled()) {
                    handlePostSingleDecode(ctx);
                    break;
                }
            }
            //assert input == null;
        } finally {
            try {
                if (cumulation == in) {
                    handleRetainedCumulationBeforeReturn(ctx);
                }
            } finally {
                releaseInput();
            }
        }
    }

    // Return targetReadable bytes (max amount that can remain in cumulation buffer
    // for us to be able to discard it, possibly replacing with the pending input buffer),
    // or -1 if we are finished (more data needed)
    private int moveSomeInputToCumulation(ChannelHandlerContext ctx, int existingBytes) {
        final int necessary = minRequired - existingBytes;
        int sufficient = maxRequired - existingBytes;
        assert sufficient >= necessary;

        final int inputIndex = input.readerIndex();
        final int newBytes = input.writerIndex() - inputIndex;
        if (newBytes < necessary) {
            // We don't have sufficient data yet, cumulate it
            if (!hasEnoughWritableSpace(cumulation, necessary)) {
                if (sufficient > necessary + 64) {
                    // Cap headroom if hypothetical max is very large
                    sufficient = calculateNewCapacity(ctx, 256) - existingBytes;
                }
                cumulation = replaceCumulation(ctx, cumulation, sufficient);
            }
            cumulation.writeBytes(input, inputIndex, newBytes);
            releaseInput();
            return -1; // need more data to make progress
        }
        int toWrite;
        if (newBytes < sufficient) {
            // We have the minimum required number of bytes but not _necessarily_ sufficient
            int nextIncrease = calculateNewCapacity(ctx, 0) - existingBytes;
            if (!hasEnoughWritableSpace(cumulation, newBytes)) {
                cumulation = replaceCumulation(ctx, cumulation, max(newBytes, nextIncrease));
            }
            toWrite = min(nextIncrease, newBytes);
        } else {
            // We definitely have enough data to make progress, concatenate only the max that
            // might be needed
            if (!hasEnoughWritableSpace(cumulation, necessary)) {
                cumulation = replaceCumulation(ctx, cumulation, sufficient);
            }
            toWrite = min(cumulation.maxFastWritableBytes(), sufficient);
        }
        cumulation.writeBytes(input, inputIndex, toWrite);
        if (toWrite != newBytes) {
            input.readerIndex(inputIndex + toWrite);
            return toWrite;
        }
        releaseInput();
        return 0;
    }

    private int calculateNewCapacity(ChannelHandlerContext ctx, int min) {
        if (maxRequired <= min || maxRequired == minRequired) {
            return maxRequired;
        }
        return minRequired <= min ? min
                : min(maxRequired, ctx.alloc().calculateNewCapacity(minRequired, Integer.MAX_VALUE));
    }

    // returns true if we are finished (more data needed)
    private boolean handleReadTargetAttained(ChannelHandlerContext ctx, int remaining) {
        // target was reached, we can discard current buffer
        cumulation.release();
        if (input == null) {
            cumulation = null;
            // more data needed to make progress, done for now
            return true;
        } else {
            if (remaining > 0) {
                // rewind input
                input.readerIndex(input.readerIndex() - remaining);
            }
            if (input.isContiguous()) {
                cumulation = input;
                input = null;
            } else {
                setCumulationFromNonContiguousInput(ctx);
            }
        }
        return false;
    }

    private void handleRetainedCumulationBeforeReturn(ChannelHandlerContext ctx) {
        assert input == null;
        // Copy to a newly allocated buffer now if we know it's needed here
        final int existingBytes = cumulation.readableBytes();
        if (neverRetainInput || minRequired > existingBytes + cumulation.maxFastWritableBytes()) {
            int target = calculateNewCapacity(ctx, 256);
            // Skip if maxRequired is too big, since we'll want to base the
            // allocation on the size of the next input received (not yet known)
            if (neverRetainInput || target == maxRequired) {
                input = cumulation;
                cumulation = null;
                cumulation = ctx.alloc().buffer(max(target, existingBytes));
                cumulation.setBytes(0, input, input.readerIndex(), existingBytes)
                    .writerIndex(existingBytes);
            }
        }
    }

    private void releaseInput() {
        if (input != null) {
            input.release();
            input = null;
        }
    }

    private void setCumulationFromNonContiguousInput(ChannelHandlerContext ctx) {
        int readableBytes = input.readableBytes();
        if (readableBytes > 512 && input instanceof CompositeByteBuf) {
            CompositeByteBuf composite = (CompositeByteBuf) input;
            int readerIndex = composite.readerIndex();
            cumulation = composite.componentSliceFromOffset(readerIndex).retain();
            int size = cumulation.readableBytes();
            int newOffset = readerIndex + size;
            if (size >= readableBytes) {
                composite.release();
                input = null;
            } else if (composite.toComponentIndex(newOffset)
                    == composite.toComponentIndex(composite.writerIndex() - 1)) {
                ByteBuf newInput = composite.componentSliceFromOffset(newOffset).retain();
                composite.release();
                input = newInput;
            } else {
                composite.readerIndex(newOffset);
            }
        } else {
            // Possibly allocate more than the size of the input if we know more will
            // need to be appended for the next decode operation
            int extra = readableBytes <= maxRequired ? 0
                    : max(0, calculateNewCapacity(ctx, 256) - readableBytes);
            cumulation = replaceCumulation(ctx, input, extra);
            input = null;
        }
    }

    private void handlePostSingleDecode(ChannelHandlerContext ctx) {
        if (input == null) {
            return;
        }
        assert cumulation != null;
        // We could make the single decode logic more sophisticated to reduce copying
        // but probably not worth the extra effort/complexity given that it's used rarely
        // and already documented to have a perf impact
        int required = max(input.readableBytes(), minRequired - cumulation.readableBytes());
        if (!hasEnoughWritableSpace(cumulation, required)) {
            cumulation = replaceCumulation(ctx, cumulation, required);
        }
        cumulation.writeBytes(input);
        releaseInput();
    }

    private void nonMergeDecode(ChannelHandlerContext ctx, ByteBuf in, CodecOutputList out) {
        try {
            first = cumulation == null;
            cumulation = cumulator.cumulate(ctx.alloc(), first ? Unpooled.EMPTY_BUFFER : cumulation, in);
            callDecode(ctx, cumulation, out);
        } finally {
            if (cumulation != null && !cumulation.isReadable()) {
                cumulation.release();
                cumulation = null;
            }
            if (cumulation == null) {
                numReads = 0;
            } else if (++ numReads >= discardAfterReads) {
                // We did enough reads already try to discard some bytes so we not risk to see a OOME.
                // See https://github.com/netty/netty/issues/4275
                numReads = 0;
                discardSomeReadBytes();
            }
        }
    }

    /**
     * Get {@code numElements} out of the {@link List} and forward these through the pipeline.
     */
    static void fireChannelRead(ChannelHandlerContext ctx, List<Object> msgs, int numElements) {
        if (msgs instanceof CodecOutputList) {
            fireChannelRead(ctx, (CodecOutputList) msgs, numElements);
        } else {
            for (int i = 0; i < numElements; i++) {
                ctx.fireChannelRead(msgs.get(i));
            }
        }
    }

    /**
     * Get {@code numElements} out of the {@link CodecOutputList} and forward these through the pipeline.
     */
    static void fireChannelRead(ChannelHandlerContext ctx, CodecOutputList msgs, int numElements) {
        for (int i = 0; i < numElements; i ++) {
            ctx.fireChannelRead(msgs.getUnsafe(i));
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (cumulator != MERGE_CUMULATOR) {
            numReads = 0;
            discardSomeReadBytes();
        }
        if (!firedChannelRead && !ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
        firedChannelRead = false;
        ctx.fireChannelReadComplete();
    }

    protected final void discardSomeReadBytes() {
        if (cumulation != null && !first && cumulation.refCnt() == 1) {
            // discard some bytes if possible to make more room in the
            // buffer but only if the refCnt == 1  as otherwise the user may have
            // used slice().retain() or duplicate().retain().
            //
            // See:
            // - https://github.com/netty/netty/issues/2327
            // - https://github.com/netty/netty/issues/1764
            cumulation.discardSomeReadBytes();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channelInputClosed(ctx, true);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ChannelInputShutdownEvent) {
            // The decodeLast method is invoked when a channelInactive event is encountered.
            // This method is responsible for ending requests in some situations and must be called
            // when the input has been shutdown.
            channelInputClosed(ctx, false);
        }
        super.userEventTriggered(ctx, evt);
    }

    private void channelInputClosed(ChannelHandlerContext ctx, boolean callChannelInactive) {
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            channelInputClosed(ctx, out);
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            try {
                if (cumulation != null) {
                    cumulation.release();
                    cumulation = null;
                }
                int size = out.size();
                fireChannelRead(ctx, out, size);
                if (size > 0) {
                    // Something was read, call fireChannelReadComplete()
                    ctx.fireChannelReadComplete();
                }
                if (callChannelInactive) {
                    ctx.fireChannelInactive();
                }
            } finally {
                // Recycle in all cases
                out.recycle();
            }
        }
    }

    /**
     * Called when the input of the channel was closed which may be because it changed to inactive or because of
     * {@link ChannelInputShutdownEvent}.
     */
    void channelInputClosed(ChannelHandlerContext ctx, List<Object> out) throws Exception {
        if (cumulation != null) {
            callDecode(ctx, cumulation, out);
            decodeLast(ctx, cumulation, out);
        } else {
            decodeLast(ctx, Unpooled.EMPTY_BUFFER, out);
        }
    }

    /**
     * Called once data should be decoded from the given {@link ByteBuf}. This method will call
     * {@link #decode(ChannelHandlerContext, ByteBuf, List)} as long as decoding should take place.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     */
    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int remaining = in.readableBytes();
        if (remaining <= 0) {
            return;
        }
        try {
            int outSize = out.size();
            for (;;) {
                if (outSize > 0) {
                    fireChannelRead(ctx, out, outSize);
                    out.clear();

                    // Check if this handler was removed before continuing with decoding.
                    // If it was removed, it is not safe to continue to operate on the buffer.
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/4635
                    if (ctx.isRemoved()) {
                        break;
                    }
                    outSize = 0;
                }

                // reset to default values prior to calling decode
                minRequired = 1;
                maxRequired = Integer.MAX_VALUE;

                decodeRemovalReentryProtection(ctx, in, out);

                // Check if this handler was removed before continuing the loop.
                // If it was removed, it is not safe to continue to operate on the buffer.
                //
                // See https://github.com/netty/netty/issues/1664
                if (ctx.isRemoved()) {
                    break;
                }

                if (minRequired < 1 || maxRequired < minRequired) {
                    throw new DecoderException(StringUtil.simpleClassName(getClass()) +
                            ".decode() set invalid required bytes values: min=" +
                            minRequired + ", max=" + maxRequired);
                }

                int remainingAfter = in.readableBytes();
                int outSizeAfter = out.size();
                boolean somethingWasDecoded = outSizeAfter > outSize;

                if (remainingAfter == remaining) {
                    // nothing was read
                    handleNothingRead(somethingWasDecoded, remainingAfter);
                    break;
                }

                if (remainingAfter < minRequired) {
                    break;
                }

                if (somethingWasDecoded && isSingleDecode()) {
                    break;
                }

                remaining = remainingAfter;
                outSize = outSizeAfter;
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception cause) {
            throw new DecoderException(cause);
        }
    }

    // Separate method to facilitate inlining of callDecode()
    private void handleNothingRead(boolean somethingWasDecoded, int remainingAfter) {
        if (somethingWasDecoded) {
            throw new DecoderException(StringUtil.simpleClassName(getClass()) +
                    ".decode() did not read anything but decoded a message.");
        }
        if (remainingAfter >= maxRequired) {
            throw new DecoderException(StringUtil.simpleClassName(getClass()) +
                    ".decode() did not read anything and set max required <= available");
        }
        if (remainingAfter >= minRequired) {
            minRequired = remainingAfter + 1;
        }
    }

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     * @throws Exception    is thrown if an error occurs
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;

    /**
     * Equivalent to {@link #setRequiredBytes(int, int) setRequiredBytes}{@code (exact, exact)}.
     *
     * @param exact the necessary and sufficient number of bytes required to make
     *     progress in the next call to {@link #decode(ChannelHandlerContext, ByteBuf, List)}
     */
    protected final void setRequiredBytes(int exact) {
        if (exact < 1) {
            throw new DecoderException(
                    "setRequiredBytes called with invalid value " + exact + ", must be > 0");
        }
        setRequiredBytes(exact, exact);
    }

    /**
     * Called by decoders from the {@link #decode(ChannelHandlerContext, ByteBuf, List)}
     * method to indicate lower bounds on how much data is required by the <i>next</i>
     * call to decode.
     * <p>
     * Decoders do not need to call this method for correct functionality, but doing so can
     * greatly reduce the overall amount of data that needs to be copied.
     * <p>
     * {@code min} indicates a known minimum number of required bytes, {@code max} indicates
     * a number of bytes which is guaranteed to be sufficient to make progress. Both should
     * ideally be otherwise as small as possible. It's required that {@code min <= max}, and
     * if {@code min == max} then {@link #setRequiredBytes(int)} can be used instead.
     * <p>
     * Notes on usage:
     * <ul>
     * <li>The effective values reset between {@code decode} invocations</li>
     * <li>It is not required to be called during any given {@code decode} invocation, and if
     * it is not then behaviour reverts to the default, which is to call with the same input
     * buffer if any bytes were consumed on the prior call and as soon as there is <i>any</i>
     * new data otherwise</li>
     * <li>Only the last call to either the one or two-arg version per {@code decode}
     * invocation will take effect</li>
     * <li>It's guaranteed the next call to {@code decode} will contain <i>at least</i>
     * {@code min} bytes</li>
     * </ul>
     *
     * @param min count of bytes required by next call to {@code decode}, or 1 if not known
     * @param max count of bytes guaranteed to be sufficient for the next call to
     *     {@code decode} to make progress, or {@link Integer#MAX_VALUE} if not known
     */
    protected void setRequiredBytes(int min, int max) {
        if (min < 1 | max < min) {
            throw new DecoderException(
                    "setRequiredBytes called with invalid values: min = " + min + ", max = " + max);
        }
        minRequired = min;
        maxRequired = max;
    }

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     * @throws Exception    is thrown if an error occurs
     */
    final void decodeRemovalReentryProtection(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        decodeState = STATE_CALLING_CHILD_DECODE;
        try {
            decode(ctx, in, out);
        } finally {
            boolean removePending = decodeState == STATE_HANDLER_REMOVED_PENDING;
            decodeState = STATE_INIT;
            if (removePending) {
                fireChannelRead(ctx, out, out.size());
                out.clear();
                handlerRemoved(ctx);
            }
        }
    }

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, ByteBuf, List)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() >= minRequired) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decodeRemovalReentryProtection(ctx, in, out);
        }
    }

    private static boolean hasEnoughWritableSpace(ByteBuf cumulation, int required) {
        return !cumulation.isReadOnly() && required <= cumulation.maxFastWritableBytes();
    }

    private static ByteBuf replaceCumulation(ChannelHandlerContext ctx, ByteBuf oldCumulation, int newBytes) {
        int existingBytes = oldCumulation.readableBytes();
        ByteBuf newCumulation = ctx.alloc().buffer(existingBytes + newBytes);
        ByteBuf toRelease = newCumulation;
        try {
            // Using setBytes avoids redundant checks and stack depth compared to calling writeBytes(...)
            newCumulation.capacity(newCumulation.maxFastWritableBytes())
                .setBytes(0, oldCumulation, oldCumulation.readerIndex(), existingBytes)
                .writerIndex(existingBytes);
            toRelease = oldCumulation;
            return newCumulation;
        } finally {
            toRelease.release();
        }
    }

    /**
     * Cumulate {@link ByteBuf}s.
     */
    public interface Cumulator {
        /**
         * Cumulate the given {@link ByteBuf}s and return the {@link ByteBuf} that holds the cumulated bytes.
         * The implementation is responsible to correctly handle the life-cycle of the given {@link ByteBuf}s and so
         * call {@link ByteBuf#release()} if a {@link ByteBuf} is fully consumed.
         */
        ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in);
    }
}

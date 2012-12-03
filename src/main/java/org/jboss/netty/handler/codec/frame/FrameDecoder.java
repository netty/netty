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
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.CompositeChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;

import java.net.SocketAddress;

/**
 * Decodes the received {@link ChannelBuffer}s into a meaningful frame object.
 * <p>
 * In a stream-based transport such as TCP/IP, packets can be fragmented and
 * reassembled during transmission even in a LAN environment.  For example,
 * let us assume you have received three packets:
 * <pre>
 * +-----+-----+-----+
 * | ABC | DEF | GHI |
 * +-----+-----+-----+
 * </pre>
 * because of the packet fragmentation, a server can receive them like the
 * following:
 * <pre>
 * +----+-------+---+---+
 * | AB | CDEFG | H | I |
 * +----+-------+---+---+
 * </pre>
 * <p>
 * {@link FrameDecoder} helps you defrag the received packets into one or more
 * meaningful <strong>frames</strong> that could be easily understood by the
 * application logic.  In case of the example above, your {@link FrameDecoder}
 * implementation could defrag the received packets like the following:
 * <pre>
 * +-----+-----+-----+
 * | ABC | DEF | GHI |
 * +-----+-----+-----+
 * </pre>
 * <p>
 * The following code shows an example handler which decodes a frame whose
 * first 4 bytes header represents the length of the frame, excluding the
 * header.
 * <pre>
 * MESSAGE FORMAT
 * ==============
 *
 * Offset:  0        4                   (Length + 4)
 *          +--------+------------------------+
 * Fields:  | Length | Actual message content |
 *          +--------+------------------------+
 *
 * DECODER IMPLEMENTATION
 * ======================
 *
 * public class IntegerHeaderFrameDecoder extends {@link FrameDecoder} {
 *
 *   {@code @Override}
 *   protected Object decode({@link ChannelHandlerContext} ctx,
 *                           {@link Channel channel},
 *                           {@link ChannelBuffer} buf) throws Exception {
 *
 *     // Make sure if the length field was received.
 *     if (buf.readableBytes() &lt; 4) {
 *        // The length field was not received yet - return null.
 *        // This method will be invoked again when more packets are
 *        // received and appended to the buffer.
 *        return <strong>null</strong>;
 *     }
 *
 *     // The length field is in the buffer.
 *
 *     // Mark the current buffer position before reading the length field
 *     // because the whole frame might not be in the buffer yet.
 *     // We will reset the buffer position to the marked position if
 *     // there's not enough bytes in the buffer.
 *     buf.markReaderIndex();
 *
 *     // Read the length field.
 *     int length = buf.readInt();
 *
 *     // Make sure if there's enough bytes in the buffer.
 *     if (buf.readableBytes() &lt; length) {
 *        // The whole bytes were not received yet - return null.
 *        // This method will be invoked again when more packets are
 *        // received and appended to the buffer.
 *
 *        // Reset to the marked position to read the length field again
 *        // next time.
 *        buf.resetReaderIndex();
 *
 *        return <strong>null</strong>;
 *     }
 *
 *     // There's enough bytes in the buffer. Read it.
 *     {@link ChannelBuffer} frame = buf.readBytes(length);
 *
 *     // Successfully decoded a frame.  Return the decoded frame.
 *     return <strong>frame</strong>;
 *   }
 * }
 * </pre>
 *
 * <h3>Returning a POJO rather than a {@link ChannelBuffer}</h3>
 * <p>
 * Please note that you can return an object of a different type than
 * {@link ChannelBuffer} in your {@code decode()} and {@code decodeLast()}
 * implementation.  For example, you could return a
 * <a href="http://en.wikipedia.org/wiki/POJO">POJO</a> so that the next
 * {@link ChannelUpstreamHandler} receives a {@link MessageEvent} which
 * contains a POJO rather than a {@link ChannelBuffer}.
 *
 * <h3>Replacing a decoder with another decoder in a pipeline</h3>
 * <p>
 * If you are going to write a protocol multiplexer, you will probably want to
 * replace a {@link FrameDecoder} (protocol detector) with another
 * {@link FrameDecoder} or {@link ReplayingDecoder} (actual protocol decoder).
 * It is not possible to achieve this simply by calling
 * {@link ChannelPipeline#replace(ChannelHandler, String, ChannelHandler)}, but
 * some additional steps are required:
 * <pre>
 * public class FirstDecoder extends {@link FrameDecoder} {
 *
 *     public FirstDecoder() {
 *         super(true); // Enable unfold
 *     }
 *
 *     {@code @Override}
 *     protected Object decode({@link ChannelHandlerContext} ctx,
 *                             {@link Channel} channel,
 *                             {@link ChannelBuffer} buf) {
 *         ...
 *         // Decode the first message
 *         Object firstMessage = ...;
 *
 *         // Add the second decoder
 *         ctx.getPipeline().addLast("second", new SecondDecoder());
 *
 *         // Remove the first decoder (me)
 *         ctx.getPipeline().remove(this);
 *
 *         if (buf.readable()) {
 *             // Hand off the remaining data to the second decoder
 *             return new Object[] { firstMessage, buf.readBytes(buf.readableBytes()) };
 *         } else {
 *             // Nothing to hand off
 *             return firstMessage;
 *         }
 *     }
 * }
 * </pre>
 *
 * @apiviz.landmark
 */
public abstract class FrameDecoder extends SimpleChannelUpstreamHandler implements LifeCycleAwareChannelHandler {

    public static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;

    private boolean unfold;
    protected ChannelBuffer cumulation;
    private volatile ChannelHandlerContext ctx;
    private int copyThreshold;
    private int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;

    protected FrameDecoder() {
        this(false);
    }

    protected FrameDecoder(boolean unfold) {
        this.unfold = unfold;
    }

    public final boolean isUnfold() {
        return unfold;
    }

    public final void setUnfold(boolean unfold) {
        if (ctx == null) {
            this.unfold = unfold;
        } else {
            throw new IllegalStateException(
                    "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    /**
     * See {@link #setMaxCumulationBufferCapacity(int)} for explaintation of this setting
     *
     */
    public final int getMaxCumulationBufferCapacity() {
        return copyThreshold;
    }

    /**
     * Set the maximal capacity of the internal cumulation ChannelBuffer to use
     * before the {@link FrameDecoder} tries to minimize the memory usage by
     * "byte copy".
     *
     *
     * What you use here really depends on your application and need. Using
     * {@link Integer#MAX_VALUE} will disable all byte copies but give you the
     * cost of a higher memory usage if big {@link ChannelBuffer}'s will be
     * received.
     *
     * By default a threshold of {@code 0} is used, which means it will
     * always copy to try to reduce memory usage
     *
     *
     * @param copyThreshold
     *            the threshold (in bytes) or {@link Integer#MAX_VALUE} to
     *            disable it. The value must be at least 0
     * @throws IllegalStateException
     *             get thrown if someone tries to change this setting after the
     *             Decoder was added to the {@link ChannelPipeline}
     */
    public final void setMaxCumulationBufferCapacity(int copyThreshold) {
        if (copyThreshold < 0) {
            throw new IllegalArgumentException("maxCumulationBufferCapacity must be >= 0");
        }
        if (ctx == null) {
            this.copyThreshold = copyThreshold;
        } else {
            throw new IllegalStateException(
                    "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    /**
     * Returns the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}.
     */
    public final int getMaxCumulationBufferComponents() {
        return maxCumulationBufferComponents;
    }

    /**
     * Sets the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}
     * and its minimum allowed value is {@code 2}.
     */
    public final void setMaxCumulationBufferComponents(int maxCumulationBufferComponents) {
        if (maxCumulationBufferComponents < 2) {
            throw new IllegalArgumentException(
                    "maxCumulationBufferComponents: " + maxCumulationBufferComponents +
                    " (expected: >= 2)");
        }

        if (ctx == null) {
            this.maxCumulationBufferComponents = maxCumulationBufferComponents;
        } else {
            throw new IllegalStateException(
                    "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) throws Exception {

        Object m = e.getMessage();
        if (!(m instanceof ChannelBuffer)) {
            ctx.sendUpstream(e);
            return;
        }

        ChannelBuffer input = (ChannelBuffer) m;
        if (!input.readable()) {
            return;
        }

        if (cumulation == null) {
            try {
                // the cumulation buffer is not created yet so just pass the input to callDecode(...) method
                callDecode(ctx, e.getChannel(), input, e.getRemoteAddress());
            } finally {
                updateCumulation(ctx, input);
            }
        } else {
            input = appendToCumulation(input);
            try {
                callDecode(ctx, e.getChannel(), input, e.getRemoteAddress());
            } finally {
                updateCumulation(ctx, input);
            }
        }
    }

    protected ChannelBuffer appendToCumulation(ChannelBuffer input) {
        ChannelBuffer cumulation = this.cumulation;
        assert cumulation.readable();
        if (cumulation instanceof CompositeChannelBuffer) {
            // Make sure the resulting cumulation buffer has no more than the configured components.
            CompositeChannelBuffer composite = (CompositeChannelBuffer) cumulation;
            if (composite.numComponents() >= maxCumulationBufferComponents) {
                cumulation = composite.copy();
            }
        }

        this.cumulation = input = ChannelBuffers.wrappedBuffer(cumulation, input);
        return input;
    }

    protected ChannelBuffer updateCumulation(ChannelHandlerContext ctx, ChannelBuffer input) {
        ChannelBuffer newCumulation;
        int readableBytes = input.readableBytes();
        if (readableBytes > 0) {
            int inputCapacity = input.capacity();

            // If input.readableBytes() == input.capacity() (i.e. input is full),
            // there's nothing to save from creating a new cumulation buffer
            // even if input.capacity() exceeds the threshold, because the new cumulation
            // buffer will have the same capacity and content with input.
            if (readableBytes < inputCapacity && inputCapacity > copyThreshold) {
                // At least one byte was consumed by callDecode() and input.capacity()
                // exceeded the threshold.
                cumulation = newCumulation = newCumulationBuffer(ctx, input.readableBytes());
                cumulation.writeBytes(input);
            } else {
                // Nothing was consumed by callDecode() or input.capacity() did not
                // exceed the threshold.
                if (input.readerIndex() != 0) {
                    cumulation = newCumulation = input.slice();
                } else {
                    cumulation = newCumulation = input;
                }
            }
        } else {
            cumulation = newCumulation = null;
        }
        return newCumulation;
    }

    @Override
    public void channelDisconnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        cleanup(ctx, e);
    }

    @Override
    public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        cleanup(ctx, e);
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Decodes the received packets so far into a frame.
     *
     * If an sub-class wants to extract a frame out of the buffer it should use
     * the {@link #extractFrame(org.jboss.netty.buffer.ChannelBuffer, int, int)} method,
     * to make optimizations easier later.
     *
     * @param ctx      the context of this handler
     * @param channel  the current channel
     * @param buffer   the cumulative buffer of received packets so far.
     *                 Note that the buffer might be empty, which means you
     *                 should not make an assumption that the buffer contains
     *                 at least one byte in your decoder implementation.
     *
     * @return the decoded frame if a full frame was received and decoded.
     *         {@code null} if there's not enough data in the buffer to decode a frame.
     */
    protected abstract Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception;

    /**
     * Decodes the received data so far into a frame when the channel is
     * disconnected.
     *
     * @param ctx      the context of this handler
     * @param channel  the current channel
     * @param buffer   the cumulative buffer of received packets so far.
     *                 Note that the buffer might be empty, which means you
     *                 should not make an assumption that the buffer contains
     *                 at least one byte in your decoder implementation.
     *
     * @return the decoded frame if a full frame was received and decoded.
     *         {@code null} if there's not enough data in the buffer to decode a frame.
     */
    protected Object decodeLast(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        return decode(ctx, channel, buffer);
    }

    private void callDecode(
            ChannelHandlerContext context, Channel channel,
            ChannelBuffer cumulation, SocketAddress remoteAddress) throws Exception {

        while (cumulation.readable()) {
            int oldReaderIndex = cumulation.readerIndex();
            Object frame = decode(context, channel, cumulation);
            if (frame == null) {
                if (oldReaderIndex == cumulation.readerIndex()) {
                    // Seems like more data is required.
                    // Let us wait for the next notification.
                    break;
                } else {
                    // Previous data has been discarded.
                    // Probably it is reading on.
                    continue;
                }
            }
            if (oldReaderIndex == cumulation.readerIndex()) {
                throw new IllegalStateException(
                        "decode() method must read at least one byte " +
                        "if it returned a frame (caused by: " + getClass() + ')');
            }

            unfoldAndFireMessageReceived(context, remoteAddress, frame);
        }
    }

    protected final void unfoldAndFireMessageReceived(
            ChannelHandlerContext context, SocketAddress remoteAddress, Object result) {
        if (unfold) {
            if (result instanceof Object[]) {
                for (Object r: (Object[]) result) {
                    Channels.fireMessageReceived(context, r, remoteAddress);
                }
            } else if (result instanceof Iterable<?>) {
                for (Object r: (Iterable<?>) result) {
                    Channels.fireMessageReceived(context, r, remoteAddress);
                }
            } else {
                Channels.fireMessageReceived(context, result, remoteAddress);
            }
        } else {
            Channels.fireMessageReceived(context, result, remoteAddress);
        }
    }

    /**
     * Gets called on {@link #channelDisconnected(ChannelHandlerContext, ChannelStateEvent)} and
     * {@link #channelClosed(ChannelHandlerContext, ChannelStateEvent)}
     */
    protected void cleanup(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        try {
            ChannelBuffer cumulation = this.cumulation;
            if (cumulation == null) {
                return;
            }

            this.cumulation = null;

            if (cumulation.readable()) {
                // Make sure all frames are read before notifying a closed channel.
                callDecode(ctx, ctx.getChannel(), cumulation, null);
            }

            // Call decodeLast() finally.  Please note that decodeLast() is
            // called even if there's nothing more to read from the buffer to
            // notify a user that the connection was closed explicitly.
            Object partialFrame = decodeLast(ctx, ctx.getChannel(), cumulation);
            if (partialFrame != null) {
                unfoldAndFireMessageReceived(ctx, null, partialFrame);
            }
        } finally {
            ctx.sendUpstream(e);
        }
    }

    /**
     * Create a new {@link ChannelBuffer} which is used for the cumulation.
     * Sub-classes may override this.
     *
     * @param ctx {@link ChannelHandlerContext} for this handler
     * @return buffer the {@link ChannelBuffer} which is used for cumulation
     */
    protected ChannelBuffer newCumulationBuffer(
            ChannelHandlerContext ctx, int minimumCapacity) {
        ChannelBufferFactory factory = ctx.getChannel().getConfig().getBufferFactory();
        return factory.getBuffer(Math.max(minimumCapacity, 256));
    }

    /**
     * Replace this {@link FrameDecoder} in the {@link ChannelPipeline} with the given {@link ChannelHandler}. All
     * remaining bytes in the {@link ChannelBuffer} will get send to the new {@link ChannelHandler} that was used
     * as replacement
     *
     */
    public void replace(String handlerName, ChannelHandler handler) {
        if (ctx == null) {
            throw new IllegalStateException(
                    "Replace cann only be called once the FrameDecoder is added to the ChannelPipeline");
        }
        ChannelPipeline pipeline = ctx.getPipeline();
        pipeline.addAfter(ctx.getName(), handlerName, handler);

        try {
            if (cumulation != null) {
                Channels.fireMessageReceived(ctx, cumulation.readBytes(actualReadableBytes()));
            }
        } finally {
            pipeline.remove(this);
        }
    }

    /**
     * Returns the actual number of readable bytes in the internal cumulative
     * buffer of this decoder.  You usually do not need to rely on this value
     * to write a decoder.  Use it only when you muse use it at your own risk.
     * This method is a shortcut to {@link #internalBuffer() internalBuffer().readableBytes()}.
     */
    protected int actualReadableBytes() {
        return internalBuffer().readableBytes();
    }

    /**
     * Returns the internal cumulative buffer of this decoder.  You usually
     * do not need to access the internal buffer directly to write a decoder.
     * Use it only when you must use it at your own risk.
     */
    protected ChannelBuffer internalBuffer() {
        ChannelBuffer buf = cumulation;
        if (buf == null) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        return buf;
    }

    /**
     * Extract a Frame of the specified buffer. By default this implementation
     * will return a extract the sub-region of the buffer and create a new one.
     * If an sub-class want to extract a frame from the buffer it should use this method
     * by default.
     *
     * <strong>Be sure that this method MUST not modify the readerIndex of the given buffer</strong>
     *
     */
    protected ChannelBuffer extractFrame(ChannelBuffer buffer, int index, int length) {
        ChannelBuffer frame = buffer.factory().getBuffer(length);
        frame.writeBytes(buffer, index, length);
        return frame;
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // Nothing to do..
    }

    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // Nothing to do..
    }

    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // Nothing to do..
    }

}

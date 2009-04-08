/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.replay;

import java.lang.reflect.Array;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

/**
 * A specialized variation of {@link FrameDecoder} which enables implementation
 * of a non-blocking decoder in the blocking I/O paradigm.
 * <p>
 * The biggest difference between {@link ReplayingDecoder} and
 * {@link FrameDecoder} is that {@link ReplayingDecoder} allows you to
 * implement the {@code decode()} and {@code decodeLast()} methods just like
 * all required bytes were received already, rather than checking the
 * availability of the required bytes.  For example, the following
 * {@link FrameDecoder} implementation:
 * <pre>
 * public class IntegerHeaderFrameDecoder extends FrameDecoder {
 *
 *   protected Object decode(ChannelHandlerContext ctx,
 *                           Channel channel,
 *                           ChannelBuffer buf) throws Exception {
 *
 *     if (buf.readableBytes() &lt; 4) {
 *        return <strong>null</strong>;
 *     }
 *
 *     buf.markReaderIndex();
 *     int length = buf.readInt();
 *
 *     if (buf.readableBytes() &lt; length) {
 *        buf.resetReaderIndex();
 *        return <strong>null</strong>;
 *     }
 *
 *     return buf.readBytes(length);
 *   }
 * }
 * </pre>
 * is simplified like the following with {@link ReplayingDecoder}:
 * <pre>
 * public class IntegerHeaderFrameDecoder
 *      extends ReplayingDecoder&lt;VoidEnum&gt; {
 *
 *   protected Object decode(ChannelHandlerContext ctx,
 *                           Channel channel,
 *                           ChannelBuffer buf,
 *                           VoidEnum state) throws Exception {
 *
 *     return buf.readBytes(buf.readInt());
 *   }
 * }
 * </pre>
 *
 * <h3>How does this work?</h3>
 * <p>
 * {@link ReplayingDecoder} passes a specialized {@link ChannelBuffer}
 * implementation which throws an {@link Error} of certain type when there's not
 * enough data in the buffer.  In the {@code IntegerHeaderFrameDecoder} above,
 * you just assumed that there will be 4 or more bytes in the buffer when
 * you call {@code buf.readInt()}.  If there's really 4 bytes in the buffer,
 * it will return the integer header as you expected.  Otherwise, the
 * {@link Error} will be raised and the control will be returned to
 * {@link ReplayingDecoder}.  If {@link ReplayingDecoder} catches the
 * {@link Error}, then it will rewind the {@code readerIndex} of the buffer
 * back to the 'initial' position (i.e. the beginning of the buffer) and call
 * the {@code decode(..)} method again when more data is received into the
 * buffer.
 * <p>
 * Please note that the overhead of throwing an {@link Error} is minimal unlike
 * throwing a new {@link Exception} in an ordinary way. {@link ReplayingDecoder}
 * reuses the same {@link Error} instance so that it does not need to fill its
 * stack trace, which takes most of {@link Exception} initialization time.
 *
 * <h3>Limitations</h3>
 * <p>
 * At the cost of the simplicity, {@link ReplayingDecoder} enforces you a few
 * limitations:
 * <ul>
 * <li>Some buffer operations are prohibited.</li>
 * <li>Performance can be worse if the network is slow and the message
 *     format is complicated unlike the example above.  In this case, your
 *     decoder might have to decode the same part of the message over and over
 *     again.</li>
 * </ul>
 *
 * <h3>Improving the performance</h3>
 * <p>
 * Fortunately, the performance of a complex decoder implementation can be
 * improved significantly with the {@code checkpoint()} method.  The
 * {@code checkpoint()} method updates the 'initial' position of the buffer so
 * that {@link ReplayingDecoder} rewinds the {@code readerIndex} of the buffer
 * to the last position where you called the {@code checkpoint()} method.
 *
 * <h4>Calling {@code checkpoint(T)} with an {@link Enum}</h4>
 * <p>
 * Although you can just use {@code checkpoint()} method and manage the state
 * of the decoder by yourself, the easiest way to manage the state of the
 * decoder is to create an {@link Enum} type which represents the current state
 * of the decoder and to call {@code checkpoint(T)} method whenever the state
 * changes.  You can have as many states as you want depending on the
 * complexity of the message you want to decode:
 *
 * <pre>
 * public enum MyDecoderState {
 *   READ_LENGTH,
 *   READ_CONTENT;
 * }
 *
 * public class IntegerHeaderFrameDecoder
 *      extends ReplayingDecoder&lt;<strong>MyDecoderState</strong>&gt; {
 *
 *   private int length;
 *
 *   public IntegerHeaderFrameDecoder() {
 *     // Set the initial state.
 *     <strong>super(MyDecoderState.READ_LENGTH);</strong>
 *   }
 *
 *   protected Object decode(ChannelHandlerContext ctx,
 *                           Channel channel,
 *                           ChannelBuffer buf,
 *                           MyDecoderState state) throws Exception {
 *     switch (state) {
 *     case READ_LENGTH:
 *       length = buf.readInt();
 *       <strong>checkpoint(MyDecoderState.READ_CONTENT);</strong>
 *     case READ_CONTENT:
 *       ChannelBuffer frame = buf.readBytes(length);
 *       <strong>checkpoint(MyDecoderState.READ_LENGTH);</strong>
 *       return frame;
 *     default:
 *       throw new Error("Shouldn't reach here.");
 *     }
 *   }
 * }
 * </pre>
 *
 * <h4>Calling {@code checkpoint()} with no parameter</h4>
 * <p>
 * An alternative way to manage the decoder state is to manage it by yourself.
 * <pre>
 * public class IntegerHeaderFrameDecoder
 *      extends ReplayingDecoder&lt;<strong>VoidEnum</strong>&gt; {
 *
 *   <strong>private boolean readLength;</strong>
 *   private int length;
 *
 *   protected Object decode(ChannelHandlerContext ctx,
 *                           Channel channel,
 *                           ChannelBuffer buf,
 *                           MyDecoderState state) throws Exception {
 *     if (!readLength) {
 *       length = buf.readInt();
 *       <strong>readLength = true;</strong>
 *       <strong>checkpoint();</strong>
 *     }
 *
 *     if (readLength) {
 *       ChannelBuffer frame = buf.readBytes(length);
 *       <strong>readLength = false;</strong>
 *       <strong>checkpoint();</strong>
 *       return frame;
 *     }
 *   }
 * }
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @param <T>
 *        the state type; use {@link VoidEnum} if state management is unused
 *
 * @apiviz.landmark
 */
@ChannelPipelineCoverage("one")
public abstract class ReplayingDecoder<T extends Enum<T>>
        extends SimpleChannelHandler implements LifeCycleAwareChannelHandler {


    private final AtomicReference<ChannelBuffer> cumulation =
        new AtomicReference<ChannelBuffer>();
    private final boolean unfold;
    private volatile ReplayingDecoderBuffer replayable;
    private volatile T state;
    private volatile int checkpoint;

    /**
     * Creates a new instance with no initial state (i.e: {@code null}).
     */
    protected ReplayingDecoder() {
        this(null);
    }

    protected ReplayingDecoder(boolean unfold) {
        this(null, unfold);
    }

    /**
     * Creates a new instance with the specified initial state.
     */
    protected ReplayingDecoder(T initialState) {
        this(initialState, false);
    }

    protected ReplayingDecoder(T initialState, boolean unfold) {
        this.state = initialState;
        this.unfold = unfold;
    }

    /**
     * Stores the internal cumulative buffer's reader position.
     */
    protected void checkpoint() {
        checkpoint = cumulation().readerIndex();
    }

    /**
     * Stores the internal cumulative buffer's reader position and updates
     * the current decoder state.
     */
    protected void checkpoint(T state) {
        checkpoint = cumulation().readerIndex();
        this.state = state;
    }

    /**
     * Returns the current state of this decoder.
     * @return the current state of this decoder
     */
    protected T getState() {
        return state;
    }

    /**
     * Sets the current state of this decoder.
     * @return the old state of this decoder
     */
    protected T setState(T newState) {
        T oldState = state;
        state = newState;
        return oldState;
    }

    /**
     * Decodes the received packets so far into a frame.
     *
     * @param ctx      the context of this handler
     * @param channel  the current channel
     * @param buffer   the cumulative buffer of received packets so far\
     * @param state    the current decoder state ({@code null} if unused)
     *
     * @return the decoded frame
     */
    protected abstract Object decode(ChannelHandlerContext ctx,
            Channel channel, ChannelBuffer buffer, T state) throws Exception;

    /**
     * Decodes the received data so far into a frame when the channel is
     * disconnected.
     *
     * @param ctx      the context of this handler
     * @param channel  the current channel
     * @param buffer   the cumulative buffer of received packets so far
     * @param state    the current decoder state ({@code null} if unused)
     *
     * @return the decoded frame
     */
    protected Object decodeLast(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, T state) throws Exception {
        return decode(ctx, channel, buffer, state);
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        cumulation(ctx);
        super.handleUpstream(ctx, e);
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        cumulation(ctx);
    }

    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // Unused
    }

    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // Unused
    }

    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // Unused
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {

        Object m = e.getMessage();
        if (!(m instanceof ChannelBuffer)) {
            ctx.sendUpstream(e);
            return;
        }

        ChannelBuffer input = (ChannelBuffer) m;
        if (!input.readable()) {
            return;
        }

        ChannelBuffer cumulation = cumulation(ctx);
        cumulation.discardReadBytes();
        cumulation.writeBytes(input);
        callDecode(ctx, e.getChannel(), cumulation, e.getRemoteAddress());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        cleanup(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        cleanup(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        ctx.sendUpstream(e);
    }

    private void callDecode(ChannelHandlerContext context, Channel channel, ChannelBuffer cumulation, SocketAddress remoteAddress) throws Exception {
        while (cumulation.readable()) {
            int oldReaderIndex = checkpoint = cumulation.readerIndex();
            Object result = null;
            T oldState = state;
            try {
                result = decode(context, channel, replayable, state);
                if (result == null) {
                    if (oldReaderIndex == cumulation.readerIndex() && oldState == state) {
                        throw new IllegalStateException(
                                "null cannot be returned if no data is consumed and state didn't change.");
                    } else {
                        // Previous data has been discarded or caused state transition.
                        // Probably it is reading on.
                        continue;
                    }
                }
            } catch (ReplayError replay) {
                // Return to the checkpoint (or oldPosition) and retry.
                cumulation.readerIndex(checkpoint);
            }

            if (result == null) {
                // Seems like more data is required.
                // Let us wait for the next notification.
                break;
            }

            if (oldReaderIndex == cumulation.readerIndex() && oldState == state) {
                throw new IllegalStateException(
                        "decode() method must consume at least one byte "
                                + "if it returned a decoded message.");
            }

            // A successful decode
            unfoldAndfireMessageReceived(context, result, remoteAddress);
        }
    }

    private void unfoldAndfireMessageReceived(
            ChannelHandlerContext context, Object result, SocketAddress remoteAddress) {
        if (unfold) {
            if (result instanceof Object[]) {
                for (Object r: (Object[]) result) {
                    Channels.fireMessageReceived(context, r, remoteAddress);
                }
            } else if (result.getClass().isArray()){
                int length = Array.getLength(result);
                for (int i = 0; i < length; i ++) {
                    Channels.fireMessageReceived(context, Array.get(result, i), remoteAddress);
                }
            } else if (result instanceof Iterable) {
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

    private void cleanup(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        ChannelBuffer cumulation = cumulation(ctx);
        try {
            if (cumulation.readable()) {
                // Make sure all data was read before notifying a closed channel.
                callDecode(ctx, e.getChannel(), cumulation, null);
                if (cumulation.readable()) {
                    // and send the remainders too if necessary.
                    Object partiallyDecoded = decodeLast(ctx, e.getChannel(), replayable, state);
                    if (partiallyDecoded != null) {
                        unfoldAndfireMessageReceived(ctx, partiallyDecoded, null);
                    }
                }
            }
        } catch (ReplayError replay) {
            // Ignore
        } finally {
            ctx.sendUpstream(e);
        }
    }


    private ChannelBuffer cumulation(ChannelHandlerContext ctx) {
        ChannelBuffer buf = cumulation.get();
        if (buf == null) {
            ChannelBufferFactory factory = ctx.getChannel().getConfig().getBufferFactory();
            buf = new UnsafeDynamicChannelBuffer(factory);
            if (cumulation.compareAndSet(null, buf)) {
                replayable = new ReplayingDecoderBuffer(buf);
            } else {
                buf = cumulation.get();
            }
        }
        return buf;
    }

    private ChannelBuffer cumulation() {
        ChannelBuffer cumulation = this.cumulation.get();
        if (cumulation == null) {
            throw new IllegalStateException("Should be called in decode() only");
        }
        return cumulation;
    }
}

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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.util.internal.Signal;

/**
 * A specialized variation of {@link ByteToMessageDecoder} which enables implementation
 * of a non-blocking decoder in the blocking I/O paradigm.
 * <p>
 * The biggest difference between {@link ReplayingDecoder} and
 * {@link FrameDecoder} is that {@link ReplayingDecoder} allows you to
 * implement the {@code decode()} and {@code decodeLast()} methods just like
 * all required bytes were received already, rather than checking the
 * availability of the required bytes.  For example, the following
 * {@link FrameDecoder} implementation:
 * <pre>
 * public class IntegerHeaderFrameDecoder extends {@link FrameDecoder} {
 *
 *   {@code @Override}
 *   protected Object decode({@link ChannelHandlerContext} ctx,
 *                           {@link Channel} channel,
 *                           {@link ByteBuf} buf) throws Exception {
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
 *      extends {@link ReplayingDecoder}&lt;{@link VoidEnum}&gt; {
 *
 *   protected Object decode({@link ChannelHandlerContext} ctx,
 *                           {@link Channel} channel,
 *                           {@link ByteBuf} buf,
 *                           {@link VoidEnum} state) throws Exception {
 *
 *     return buf.readBytes(buf.readInt());
 *   }
 * }
 * </pre>
 *
 * <h3>How does this work?</h3>
 * <p>
 * {@link ReplayingDecoder} passes a specialized {@link ByteBuf}
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
 * Please note that {@link ReplayingDecoder} always throws the same cached
 * {@link Error} instance to avoid the overhead of creating a new {@link Error}
 * and filling its stack trace for every throw.
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
 * <li>You must keep in mind that {@code decode(..)} method can be called many
 *     times to decode a single message.  For example, the following code will
 *     not work:
 * <pre> public class MyDecoder extends {@link ReplayingDecoder}&lt;{@link Void}&gt; {
 *
 *   private final Queue&lt;Integer&gt; values = new LinkedList&lt;Integer&gt;();
 *
 *   {@code @Override}
 *   public Object decode(.., {@link ByteBuf} buffer, ..) throws Exception {
 *
 *     // A message contains 2 integers.
 *     values.offer(buffer.readInt());
 *     values.offer(buffer.readInt());
 *
 *     // This assertion will fail intermittently since values.offer()
 *     // can be called more than two times!
 *     assert values.size() == 2;
 *     return values.poll() + values.poll();
 *   }
 * }</pre>
 *      The correct implementation looks like the following, and you can also
 *      utilize the 'checkpoint' feature which is explained in detail in the
 *      next section.
 * <pre> public class MyDecoder extends {@link ReplayingDecoder}&lt;{@link Void}&gt; {
 *
 *   private final Queue&lt;Integer&gt; values = new LinkedList&lt;Integer&gt;();
 *
 *   {@code @Override}
 *   public Object decode(.., {@link ByteBuf} buffer, ..) throws Exception {
 *
 *     // Revert the state of the variable that might have been changed
 *     // since the last partial decode.
 *     values.clear();
 *
 *     // A message contains 2 integers.
 *     values.offer(buffer.readInt());
 *     values.offer(buffer.readInt());
 *
 *     // Now we know this assertion will never fail.
 *     assert values.size() == 2;
 *     return values.poll() + values.poll();
 *   }
 * }</pre>
 *     </li>
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
 *      extends {@link ReplayingDecoder}&lt;<strong>MyDecoderState</strong>&gt; {
 *
 *   private int length;
 *
 *   public IntegerHeaderFrameDecoder() {
 *     // Set the initial state.
 *     <strong>super(MyDecoderState.READ_LENGTH);</strong>
 *   }
 *
 *   {@code @Override}
 *   protected Object decode({@link ChannelHandlerContext} ctx,
 *                           {@link Channel} channel,
 *                           {@link ByteBuf} buf,
 *                           <b>MyDecoderState</b> state) throws Exception {
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
 *      extends {@link ReplayingDecoder}&lt;<strong>{@link Void}</strong>&gt; {
 *
 *   <strong>private boolean readLength;</strong>
 *   private int length;
 *
 *   {@code @Override}
 *   protected Object decode({@link ChannelHandlerContext} ctx,
 *                           {@link Channel} channel,
 *                           {@link ByteBuf} buf,
 *                           {@link Void} state) throws Exception {
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
 * <h3>Replacing a decoder with another decoder in a pipeline</h3>
 * <p>
 * If you are going to write a protocol multiplexer, you will probably want to
 * replace a {@link ReplayingDecoder} (protocol detector) with another
 * {@link ReplayingDecoder} or {@link FrameDecoder} (actual protocol decoder).
 * It is not possible to achieve this simply by calling
 * {@link ChannelPipeline#replace(ChannelHandler, String, ChannelHandler)}, but
 * some additional steps are required:
 * <pre>
 * public class FirstDecoder extends {@link ReplayingDecoder}&lt;{@link Void}&gt; {
 *
 *     public FirstDecoder() {
 *         super(true); // Enable unfold
 *     }
 *
 *     {@code @Override}
 *     protected Object decode({@link ChannelHandlerContext} ctx,
 *                             {@link Channel} ch,
 *                             {@link ByteBuf} buf,
 *                             {@link Void} state) {
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
 *             return new Object[] { firstMessage, buf.readBytes(<b>super.actualReadableBytes()</b>) };
 *         } else {
 *             // Nothing to hand off
 *             return firstMessage;
 *         }
 *     }
 * </pre>
 * @param <S>
 *        the state type which is usually an {@link Enum}; use {@link Void} if state management is
 *        unused
 *
 * @apiviz.landmark
 * @apiviz.has io.netty.handler.codec.UnreplayableOperationException oneway - - throws
 */
public abstract class ReplayingDecoder<O, S> extends ByteToMessageDecoder<O> {

    static final Signal REPLAY = new Signal(ReplayingDecoder.class.getName() + ".REPLAY");

    private final ByteBuf cumulation = Unpooled.buffer();
    private final ReplayingDecoderBuffer replayable = new ReplayingDecoderBuffer(cumulation);
    private S state;
    private int checkpoint = -1;

    /**
     * Creates a new instance with no initial state (i.e: {@code null}).
     */
    protected ReplayingDecoder() {
        this(null);
    }

    /**
     * Creates a new instance with the specified initial state.
     */
    protected ReplayingDecoder(S initialState) {
        state = initialState;
    }

    /**
     * Stores the internal cumulative buffer's reader position.
     */
    protected void checkpoint() {
        checkpoint = cumulation.readerIndex();
    }

    /**
     * Stores the internal cumulative buffer's reader position and updates
     * the current decoder state.
     */
    protected void checkpoint(S state) {
        checkpoint();
        state(state);
    }

    /**
     * Returns the current state of this decoder.
     * @return the current state of this decoder
     */
    protected S state() {
        return state;
    }

    /**
     * Sets the current state of this decoder.
     * @return the old state of this decoder
     */
    protected S state(S newState) {
        S oldState = state;
        state = newState;
        return oldState;
    }

    /**
     * Returns the actual number of readable bytes in the internal cumulative
     * buffer of this decoder. You usually do not need to rely on this value
     * to write a decoder. Use it only when you muse use it at your own risk.
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
        return cumulation;
    }

    @Override
    public ByteBuf newInboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        return cumulation;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        replayable.terminate();
        ByteBuf in = cumulation;
        if (in.readable()) {
            callDecode(ctx);
        }

        try {
            if (ChannelHandlerUtil.unfoldAndAdd(ctx, decodeLast(ctx, replayable), true)) {
                fireInboundBufferUpdated(ctx, in);
            }
        } catch (Signal replay) {
            // Ignore
            replay.expect(REPLAY);
        } catch (Throwable t) {
            if (t instanceof CodecException) {
                ctx.fireExceptionCaught(t);
            } else {
                ctx.fireExceptionCaught(new DecoderException(t));
            }
        }

        ctx.fireChannelInactive();
    }

    @Override
    protected void callDecode(ChannelHandlerContext ctx) {
        ByteBuf in = cumulation;
        boolean decoded = false;
        while (in.readable()) {
            try {
                int oldReaderIndex = checkpoint = in.readerIndex();
                Object result = null;
                S oldState = state;
                try {
                    result = decode(ctx, replayable);
                    if (result == null) {
                        if (oldReaderIndex == in.readerIndex() && oldState == state) {
                            throw new IllegalStateException(
                                    "null cannot be returned if no data is consumed and state didn't change.");
                        } else {
                            // Previous data has been discarded or caused state transition.
                            // Probably it is reading on.
                            continue;
                        }
                    }
                } catch (Signal replay) {
                    replay.expect(REPLAY);
                    // Return to the checkpoint (or oldPosition) and retry.
                    int checkpoint = this.checkpoint;
                    if (checkpoint >= 0) {
                        in.readerIndex(checkpoint);
                    } else {
                        // Called by cleanup() - no need to maintain the readerIndex
                        // anymore because the buffer has been released already.
                    }
                }

                if (result == null) {
                    // Seems like more data is required.
                    // Let us wait for the next notification.
                    break;
                }

                if (oldReaderIndex == in.readerIndex() && oldState == state) {
                    throw new IllegalStateException(
                            "decode() method must consume at least one byte " +
                            "if it returned a decoded message (caused by: " +
                            getClass() + ')');
                }

                // A successful decode
                if (ChannelHandlerUtil.unfoldAndAdd(ctx, result, true)) {
                    decoded = true;
                }
            } catch (Throwable t) {
                if (decoded) {
                    decoded = false;
                    fireInboundBufferUpdated(ctx, in);
                }

                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new DecoderException(t));
                }
            }
        }

        if (decoded) {
            fireInboundBufferUpdated(ctx, in);
        }
    }

    private void fireInboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) {
        final int oldReaderIndex = in.readerIndex();
        in.unsafe().discardSomeReadBytes();
        final int newReaderIndex = in.readerIndex();
        checkpoint -= oldReaderIndex - newReaderIndex;
        ctx.fireInboundBufferUpdated();
    }
}

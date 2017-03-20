/*
 * Copyright 2017 The Netty Project
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
import io.netty.handler.codec.serialization.ObjectDecoder;

import java.util.List;

/**
 * Encapsulates common logic and state for message decoders that attempt to detect frames
 * of a certain length
 */
public abstract class BaseLengthBasedFrameDecoder extends ByteToMessageDecoder {

    protected final int maxFrameLength;
    protected final boolean failFast;
    protected boolean discardingTooLongFrame;
    protected long tooLongFrameLength;
    protected long bytesToDiscard;

    protected BaseLengthBasedFrameDecoder(int maxFrameLength, boolean failFast) {
        this.maxFrameLength = maxFrameLength;
        this.failFast = failFast;
    }

    /**
     * Fail the decoding due to a frame that is too long, as per {@link #maxFrameLength}
     *
     * @param firstDetectionOfTooLongFrame true if this is the first time the too-long frame was detected
     * @return true if all bytes from the too-long frame were discarded
     */
    protected boolean failIfNecessary(boolean firstDetectionOfTooLongFrame) {
        if (bytesToDiscard == 0) {
            // Reset to the initial state and tell the handlers that
            // the frame was too large.
            final long prevTooLongFrameLength = tooLongFrameLength;
            tooLongFrameLength = 0;
            discardingTooLongFrame = false;
            if (!failFast || firstDetectionOfTooLongFrame) {
                fail(prevTooLongFrameLength);
            }
            return true;
        }
        // Keep discarding and notify handlers if necessary.
        if (failFast && firstDetectionOfTooLongFrame) {
            fail(tooLongFrameLength);
        }
        return false;
    }

    protected void resetStateOnTooLongFrame() {
        // do nothing by default
    }

    /**
     * Extract the sub-region of the specified buffer.
     * <p>
     * If you are sure that the frame and its content are not accessed after
     * the current {@link #decode(ChannelHandlerContext, ByteBuf)}
     * call returns, you can even avoid memory copy by returning the sliced
     * sub-region (i.e. <tt>return buffer.slice(index, length)</tt>).
     * It's often useful when you convert the extracted frame into an object.
     * Refer to the source code of {@link ObjectDecoder} to see how this method
     * is overridden to avoid memory copy.
     */
    protected ByteBuf extractFrame(ByteBuf buffer, int index, int length) {
        return buffer.retainedSlice(index, length);
    }

    /**
     * @deprecated - use {@link #extractFrame(ByteBuf, int, int)} instead, since the
     * {@link ChannelHandlerContext} is not actually needed for implementation
     */
    @Deprecated
    protected ByteBuf extractFrame(@SuppressWarnings({ "unused", "squid:S1172" }) ChannelHandlerContext ignored,
                                   ByteBuf buffer, int index, int length) {
        return buffer.retainedSlice(index, length);
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    protected abstract Object decode(@SuppressWarnings({ "unused", "squid:S1172" }) ChannelHandlerContext ctx,
                                     ByteBuf in) throws Exception;

    protected void discardingTooLongFrame(ByteBuf in) {
        long bytesToDiscard = this.bytesToDiscard;
        int localBytesToDiscard = (int) Math.min(bytesToDiscard, in.readableBytes());
        in.skipBytes(localBytesToDiscard);
        bytesToDiscard -= localBytesToDiscard;
        this.bytesToDiscard = bytesToDiscard;

        failIfNecessary(false);
    }

    protected void fail(long frameLength) {
        resetStateOnTooLongFrame();
        if (frameLength > 0) {
            throw new TooLongFrameException(
                "Adjusted frame length exceeds " + maxFrameLength +
                    ": " + frameLength + " - discarded");
        } else {
            throw new TooLongFrameException(
                "Adjusted frame length exceeds " + maxFrameLength +
                    " - discarding");
        }
    }
}

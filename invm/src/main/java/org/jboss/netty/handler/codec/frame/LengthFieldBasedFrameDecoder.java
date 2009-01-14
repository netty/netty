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
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 * TODO Documentation
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 * @apiviz.landmark
 */
public class LengthFieldBasedFrameDecoder extends FrameDecoder {

    private final int maxFrameLength;
    private final int lengthFieldOffset;
    private final int lengthFieldLength;
    private final int lengthFieldEndOffset;
    private final int lengthAdjustment;
    private final int initialBytesToStrip;
    private volatile boolean discardingTooLongFrame;
    private volatile long tooLongFrameLength;
    private volatile long bytesToDiscard;

    /**
     * Creates a new instance.
     */
    public LengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthFieldOffset, int lengthFieldLength) {
        this(maxFrameLength, lengthFieldOffset, lengthFieldLength, 0, 0);
    }

    /**
     * Creates a new instance.
     */
    public LengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip) {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException(
                    "maxFrameLength must be a positive integer: " +
                    maxFrameLength);
        }

        if (lengthFieldOffset < 0) {
            throw new IllegalArgumentException(
                    "lengthFieldOffset must be a non-negative integer: " +
                    lengthFieldOffset);
        }

        if (initialBytesToStrip < 0) {
            throw new IllegalArgumentException(
                    "initialBytesToStrip must be a non-negative integer: " +
                    initialBytesToStrip);
        }

        if (lengthFieldLength != 1 && lengthFieldLength != 2 &&
            lengthFieldLength != 3 && lengthFieldLength != 4 &&
            lengthFieldLength != 8) {
            throw new IllegalArgumentException(
                    "lengthFieldLength must be either 1, 2, 3, 4, or 8: " +
                    lengthFieldLength);
        }

        if (lengthFieldOffset > maxFrameLength - lengthFieldLength) {
            throw new IllegalArgumentException(
                    "maxFrameLength (" + maxFrameLength + ") " +
                    "must be equal to or greater than " +
                    "lengthFieldOffset (" + lengthFieldOffset + ") + " +
                    "lengthFieldLength (" + lengthFieldLength + ").");
        }

        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
        this.initialBytesToStrip = initialBytesToStrip;
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {

        if (discardingTooLongFrame) {
            long bytesToDiscard = this.bytesToDiscard;
            int localBytesToDiscard = (int) Math.min(bytesToDiscard, buffer.readableBytes());
            buffer.skipBytes(localBytesToDiscard);
            bytesToDiscard -= localBytesToDiscard;
            this.bytesToDiscard = bytesToDiscard;
            if (bytesToDiscard == 0) {
                // Reset to the initial state and tell the handlers that
                // the frame was too large.
                discardingTooLongFrame = false;
                long tooLongFrameLength = this.tooLongFrameLength;
                this.tooLongFrameLength = 0;
                throw new TooLongFrameException(
                        "Adjusted frame length exceeds " + maxFrameLength +
                        ": " + tooLongFrameLength);
            } else {
                // Keep discarding.
                return null;
            }
        }

        if (buffer.readableBytes() < lengthFieldEndOffset) {
            return null;
        }

        int actualLengthFieldOffset = buffer.readerIndex() + lengthFieldOffset;
        long frameLength;
        switch (lengthFieldLength) {
        case 1:
            frameLength = buffer.getUnsignedByte(actualLengthFieldOffset);
            break;
        case 2:
            frameLength = buffer.getUnsignedShort(actualLengthFieldOffset);
            break;
        case 3:
            frameLength = buffer.getUnsignedMedium(actualLengthFieldOffset);
            break;
        case 4:
            frameLength = buffer.getUnsignedInt(actualLengthFieldOffset);
            break;
        case 8:
            frameLength = buffer.getLong(actualLengthFieldOffset);
            break;
        default:
            throw new Error("should not reach here");
        }

        if (frameLength < 0) {
            buffer.skipBytes(lengthFieldEndOffset);
            throw new CorruptedFrameException(
                    "negative pre-adjustment length field: " + frameLength);
        }

        frameLength += lengthAdjustment + lengthFieldEndOffset;
        if (frameLength < lengthFieldEndOffset) {
            buffer.skipBytes(lengthFieldEndOffset);
            throw new CorruptedFrameException(
                    "Adjusted frame length (" + frameLength + ") is less " +
                    "than lengthFieldEndOffset: " + lengthFieldEndOffset);
        }

        if (frameLength > maxFrameLength) {
            // Enter the discard mode and discard everything received so far.
            discardingTooLongFrame = true;
            tooLongFrameLength = frameLength;
            bytesToDiscard = frameLength - buffer.readableBytes();
            buffer.skipBytes(buffer.readableBytes());
            return null;
        }

        // never overflows because it's less than maxFrameLength
        int frameLengthInt = (int) frameLength;
        if (buffer.readableBytes() < frameLengthInt) {
            return null;
        }

        if (initialBytesToStrip > frameLengthInt) {
            buffer.skipBytes(frameLengthInt);
            throw new CorruptedFrameException(
                    "Adjusted frame length (" + frameLength + ") is less " +
                    "than initialBytesToStrip: " + initialBytesToStrip);
        }
        buffer.skipBytes(initialBytesToStrip);
        return buffer.readBytes(frameLengthInt - initialBytesToStrip);
    }
}

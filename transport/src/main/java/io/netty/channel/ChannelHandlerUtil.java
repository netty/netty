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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Freeable;
import io.netty.buffer.MessageBuf;

/**
 * Utilities for {@link ChannelHandler} implementations.
 */
public final class ChannelHandlerUtil {

    /**
     * Unfold the given msg and pass it to the next buffer depending on the msg type.
     *
     * @param ctx
     *          the {@link ChannelHandlerContext} on which to operate
     * @param msg
     *          the msg to unfold and pass to the next buffer
     * @param inbound
     *          {@code true} if it is an inbound message, {@code false} otherwise
     * @return added
     *          {@code true} if the message was added to the next {@link ByteBuf} or {@link MessageBuf}
     * @throws Exception
     *          thrown if an error accour
     */
    public static boolean unfoldAndAdd(
            ChannelHandlerContext ctx, Object msg, boolean inbound) throws Exception {
        if (msg == null) {
            return false;
        }
        if (msg instanceof FoldedMessage) {
            FoldedMessage foldedMsg = (FoldedMessage) msg;
            boolean added = false;
            while (foldedMsg.hasNext()) {
                if (unfoldAndAdd(ctx, foldedMsg.next(), inbound)) {
                    added = true;
                }
            }
            return added;
        }
        return addToBuffer(ctx, msg, inbound);
    }

    private static boolean addToBuffer(ChannelHandlerContext ctx, Object msg, boolean inbound) {
        if (inbound) {
            if (ctx.hasNextInboundMessageBuffer()) {
                return ctx.nextInboundMessageBuffer().offer(msg);
            }

            if (msg instanceof ByteBuf && ctx.hasNextInboundByteBuffer()) {
                ByteBuf altDst = ctx.nextInboundByteBuffer();
                ByteBuf src = (ByteBuf) msg;
                if (!altDst.checkWritable(src.readableBytes())) {
                    return false;
                }
                altDst.writeBytes(src, src.readerIndex(), src.readableBytes());
                return true;
            }
        } else {
            if (ctx.hasNextOutboundMessageBuffer()) {
                ctx.nextOutboundMessageBuffer().add(msg);
                return true;
            }

            if (msg instanceof ByteBuf && ctx.hasNextOutboundByteBuffer()) {
                ByteBuf altDst = ctx.nextOutboundByteBuffer();
                ByteBuf src = (ByteBuf) msg;
                altDst.writeBytes(src, src.readerIndex(), src.readableBytes());
                return true;
            }
        }

        throw new NoSuchBufferException(String.format(
                "the handler '%s' could not find a %s which accepts a %s.",
                ctx.name(),
                inbound? ChannelInboundHandler.class.getSimpleName()
                        : ChannelOutboundHandler.class.getSimpleName(),
                msg.getClass().getSimpleName()));
    }

    private static final Class<?>[] EMPTY_TYPES = new Class<?>[0];

    /**
     * Creates a safe copy of the given array and return it.
     */
    public static Class<?>[] acceptedMessageTypes(Class<?>[] acceptedMsgTypes) {
        if (acceptedMsgTypes == null) {
            return EMPTY_TYPES;
        }

        int numElem = 0;
        for (Class<?> c: acceptedMsgTypes) {
            if (c == null) {
                break;
            }
            numElem ++;
        }

        Class<?>[] newAllowedMsgTypes = new Class[numElem];
        System.arraycopy(acceptedMsgTypes, 0, newAllowedMsgTypes, 0, numElem);

        return newAllowedMsgTypes;
    }

    /**
     * Return {@code true} if the given msg is compatible with one of the given acceptedMessageTypes or if
     * acceptedMessageTypes is null / empty.
     */
    public static boolean acceptMessage(Class<?>[] acceptedMsgTypes, Object msg) {
        if (acceptedMsgTypes == null || acceptedMsgTypes.length == 0) {
            return true;
        }

        for (Class<?> c: acceptedMsgTypes) {
            if (c.isInstance(msg)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Add the given msg to the next outbound {@link MessageBuf}.
     */
    public static void addToNextOutboundBuffer(ChannelHandlerContext ctx, Object msg) {
        try {
            ctx.nextOutboundMessageBuffer().add(msg);
        } catch (NoSuchBufferException e) {
            NoSuchBufferException newE =
                    new NoSuchBufferException(e.getMessage() + " (msg: " + msg + ')');
            newE.setStackTrace(e.getStackTrace());
            throw newE;
        }
    }

    /**
     * Add the given msg to the next inbound {@link MessageBuf}.
     */
    public static boolean addToNextInboundBuffer(ChannelHandlerContext ctx, Object msg) {
        try {
            return ctx.nextInboundMessageBuffer().offer(msg);
        } catch (NoSuchBufferException e) {
            NoSuchBufferException newE =
                    new NoSuchBufferException(e.getMessage() + " (msg: " + msg + ')');
            newE.setStackTrace(e.getStackTrace());
            throw newE;
        }
    }

    /**
     * Try to free up resources that are held by the message.
     */
    public static void freeMessage(Object msg) throws Exception {
        if (msg instanceof Freeable) {
            try {
                ((Freeable) msg).free();
            } catch (UnsupportedOperationException e) {
                // This can happen for derived buffers
                // TODO: Think about this
            }
        }
    }

    public static boolean isComplete(Object msg) {
        if (msg instanceof FoldedMessage) {
            return !((FoldedMessage) msg).hasNext();
        }
        return true;
    }

    private ChannelHandlerUtil() {
        // Unused
    }
}

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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.NoSuchBufferException;

final class CodecUtil {

    static boolean unfoldAndAdd(
            ChannelHandlerContext ctx, Object msg, boolean inbound) throws Exception {
        if (msg == null) {
            return false;
        }

        // Note we only recognize Object[] because Iterable is often implemented by user messages.
        if (msg instanceof Object[]) {
            Object[] array = (Object[]) msg;
            if (array.length == 0) {
                return false;
            }

            boolean added = false;
            for (Object m: array) {
                if (m == null) {
                    break;
                }
                if (unfoldAndAdd(ctx, m, inbound)) {
                    added = true;
                }
            }
            return added;
        }

        if (inbound) {
            if (ctx.hasNextInboundMessageBuffer()) {
                ctx.nextInboundMessageBuffer().add(msg);
                return true;
            }

            if (msg instanceof ByteBuf && ctx.hasNextInboundByteBuffer()) {
                ByteBuf altDst = ctx.nextInboundByteBuffer();
                ByteBuf src = (ByteBuf) msg;
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

    static Class<?>[] acceptedMessageTypes(Class<?>[] acceptedMsgTypes) {
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

    static boolean acceptMessage(Class<?>[] acceptedMsgTypes, Object msg) {
        if (acceptedMsgTypes.length == 0) {
            return true;
        }

        for (Class<?> c: acceptedMsgTypes) {
            if (c.isInstance(msg)) {
                return true;
            }
        }

        return false;
    }

    static void addToNextOutboundBuffer(ChannelHandlerContext ctx, Object msg) {
        try {
            ctx.nextOutboundMessageBuffer().add(msg);
        } catch (NoSuchBufferException e) {
            NoSuchBufferException newE =
                    new NoSuchBufferException(e.getMessage() + " (msg: " + msg + ')');
            newE.setStackTrace(e.getStackTrace());
            throw newE;
        }
    }

    static void addToNextInboundBuffer(ChannelHandlerContext ctx, Object msg) {
        try {
            ctx.nextInboundMessageBuffer().add(msg);
        } catch (NoSuchBufferException e) {
            NoSuchBufferException newE =
                    new NoSuchBufferException(e.getMessage() + " (msg: " + msg + ')');
            newE.setStackTrace(e.getStackTrace());
            throw newE;
        }
    }

    private CodecUtil() {
        // Unused
    }
}

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

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandlerContext;
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

            if (msg instanceof ChannelBuffer && ctx.hasNextInboundByteBuffer()) {
                ChannelBuffer altDst = ctx.nextInboundByteBuffer();
                ChannelBuffer src = (ChannelBuffer) msg;
                altDst.writeBytes(src, src.readerIndex(), src.readableBytes());
                return true;
            }
        } else {
            if (ctx.hasNextOutboundMessageBuffer()) {
                ctx.nextOutboundMessageBuffer().add(msg);
                return true;
            }

            if (msg instanceof ChannelBuffer && ctx.hasNextOutboundByteBuffer()) {
                ChannelBuffer altDst = ctx.nextOutboundByteBuffer();
                ChannelBuffer src = (ChannelBuffer) msg;
                altDst.writeBytes(src, src.readerIndex(), src.readableBytes());
                return true;
            }
        }

        throw new NoSuchBufferException();
    }

    private CodecUtil() {
        // Unused
    }
}

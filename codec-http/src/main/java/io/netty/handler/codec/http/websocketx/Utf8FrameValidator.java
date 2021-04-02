/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.CorruptedFrameException;

/**
 *
 */
public class Utf8FrameValidator extends ChannelInboundHandlerAdapter {

    private int fragmentedFramesCount;
    private Utf8Validator utf8Validator;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;

            try {
                // Processing for possible fragmented messages for text and binary
                // frames
                if (((WebSocketFrame) msg).isFinalFragment()) {
                    // Final frame of the sequence. Apparently ping frames are
                    // allowed in the middle of a fragmented message
                    if (!(frame instanceof PingWebSocketFrame)) {
                        fragmentedFramesCount = 0;

                        // Check text for UTF8 correctness
                        if ((frame instanceof TextWebSocketFrame) ||
                                (utf8Validator != null && utf8Validator.isChecking())) {
                            // Check UTF-8 correctness for this payload
                            checkUTF8String(frame.content());

                            // This does a second check to make sure UTF-8
                            // correctness for entire text message
                            utf8Validator.finish();
                        }
                    }
                } else {
                    // Not final frame so we can expect more frames in the
                    // fragmented sequence
                    if (fragmentedFramesCount == 0) {
                        // First text or binary frame for a fragmented set
                        if (frame instanceof TextWebSocketFrame) {
                            checkUTF8String(frame.content());
                        }
                    } else {
                        // Subsequent frames - only check if init frame is text
                        if (utf8Validator != null && utf8Validator.isChecking()) {
                            checkUTF8String(frame.content());
                        }
                    }

                    // Increment counter
                    fragmentedFramesCount++;
                }
            } catch (CorruptedWebSocketFrameException e) {
                frame.release();
                throw e;
            }
        }

        super.channelRead(ctx, msg);
    }

    private void checkUTF8String(ByteBuf buffer) {
        if (utf8Validator == null) {
            utf8Validator = new Utf8Validator();
        }
        utf8Validator.check(buffer);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof CorruptedFrameException && ctx.channel().isOpen()) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        super.exceptionCaught(ctx, cause);
    }
}

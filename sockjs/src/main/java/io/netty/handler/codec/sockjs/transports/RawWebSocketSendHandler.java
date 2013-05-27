/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.transports;

import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

class RawWebSocketSendHandler extends ChannelOutboundHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(RawWebSocketSendHandler.class);

    @Override
    public void write(final ChannelHandlerContext ctx, final MessageList<Object> msgs, final ChannelPromise promise)
            throws Exception {
        final int size = msgs.size();
        final MessageList<TextWebSocketFrame> frames = MessageList.newInstance();
        for (int i = 0; i < size; i ++) {
            final Object obj = msgs.get(i);
            if (obj instanceof Frame) {
                final Frame sockJSFrame = (Frame) obj;
                if (sockJSFrame instanceof MessageFrame) {
                    final MessageFrame mf = (MessageFrame) sockJSFrame;
                    final List<String> messages = mf.messages();
                    for (String message : messages) {
                        logger.debug("writing message : " + message);
                        frames.add(new TextWebSocketFrame(message));
                    }
                }
            }
        }
        msgs.recycle();
        ctx.write(frames);
        promise.setSuccess();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }

}

/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.sockjs.transport;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.util.ReferenceCountUtil;

class WebSocketSendHandler extends ChannelHandlerAdapter {

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame sockJsFrame = (Frame) msg;
            final TextWebSocketFrame webSocketFrame = new TextWebSocketFrame(sockJsFrame.content());
            ctx.writeAndFlush(webSocketFrame).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        promise.setSuccess();
                    }
                }
            });
        } else {
            ctx.write(ReferenceCountUtil.retain(msg), promise);
        }
    }

}

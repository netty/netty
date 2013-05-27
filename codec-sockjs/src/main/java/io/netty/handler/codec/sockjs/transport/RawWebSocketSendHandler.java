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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;

import java.util.List;

final class RawWebSocketSendHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof MessageFrame) {
            final MessageFrame messageFrame = (MessageFrame) msg;
            final List<String> messages = messageFrame.messages();
            final int length = messages.size();
            for (int i = 1; i <= length; i++) {
                if (i == length) {
                    ctx.write(new TextWebSocketFrame(messages.get(i)), promise);
                } else {
                    ctx.write(new TextWebSocketFrame(messages.get(i)));
                }
            }
        } else {
            ctx.write(msg, promise);
        }
    }

}

/*
 *
 *  * Copyright 2020 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License. You may obtain a
 *  * copy of the License at:
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License
 *  * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  * or implied. See the License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */

package io.netty.example.nettymsg;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * server heartbeat handler
 */
public class HeartbeatRespHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyMessage message = (NettyMessage) msg;

        if (message.getHeader() != null && message.getHeader().getType() == Header.MessageType.HEARTBEAT_REQ.value) {
            System.out.println("[recv] server received heartbeat from client ---> " + message);
            NettyMessage heartbeat = new NettyMessage();
            Header header = new Header();
            header.setType(Header.MessageType.HEARTBEAT_RESP.value);
            heartbeat.setHeader(header);
            heartbeat.setBody("heartbeat server resp @" + System.currentTimeMillis());
            System.out.println("[send] server send heartbeat resp to client ---> " + heartbeat);
            ctx.writeAndFlush(heartbeat);
        } else {
            ctx.fireChannelRead(msg);
        }
    }
}

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
 * client auth request handler
 */
public class LoginAuthReqHandler extends ChannelInboundHandlerAdapter {

    private NettyMessage buildAuthReq() {
        NettyMessage message = new NettyMessage();
        Header header = new Header(Header.MessageType.HANDSHAKE_REQ.value);
        message.setHeader(header);
        return message;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("login auth req handler active ...");
        NettyMessage message = buildAuthReq();
        System.out.println("[send] client send login req ---> " + message);
        ctx.writeAndFlush(message);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyMessage message = (NettyMessage) msg;
        if (message.getHeader() != null && message.getHeader().getType() == Header.MessageType.HANDSHAKE_RESP.value) {
            System.out.println("[recv] client receive login resp ---> " + message);
            byte loginResult = (Byte) message.getBody();
            if (loginResult != (byte) 0) {
                System.out.println("handshake fail!");
                ctx.close();
            } else {
                System.out.println("handshake success.");
                ctx.fireChannelRead(msg);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}

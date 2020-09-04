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

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * server auth handler
 */
public class LoginAuthRespHandler extends ChannelInboundHandlerAdapter {

    private Map<String, Boolean> nodeCheck = new ConcurrentHashMap<String, Boolean>();
    private String[] whiteList = new String[] {"127.0.0.1"};

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyMessage message = (NettyMessage) msg;
        if (message.getHeader() != null && message.getHeader().getType() == Header.MessageType.HANDSHAKE_REQ.value) {
            String nodeIndex = ctx.channel().remoteAddress().toString();
            NettyMessage loginResp;
            if (nodeCheck.containsKey(nodeIndex)) {
                loginResp = buildResponse((byte) -1);
            } else {
                InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
                String ip = address.getAddress().getHostAddress();
                boolean ok = false;
                for (String e : whiteList) {
                    if (e.equals(ip)) {
                        ok = true;
                        break;
                    }
                }
                loginResp = ok ? buildResponse((byte) 0) : buildResponse((byte) -1);
                if (ok) {
                    nodeCheck.put(nodeIndex, true);
                }
            }
            System.out.println("[send] login response :" + loginResp + " , body:" + loginResp.getBody());
            ctx.writeAndFlush(loginResp);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private NettyMessage buildResponse(byte resp) {
        NettyMessage message = new NettyMessage();
        Header header = new Header();
        header.setType(Header.MessageType.HANDSHAKE_RESP.value);
        message.setHeader(header);
        message.setBody(resp);
        return message;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        nodeCheck.remove(ctx.channel().remoteAddress().toString());
        ctx.close();
        ctx.fireExceptionCaught(cause);
    }
}

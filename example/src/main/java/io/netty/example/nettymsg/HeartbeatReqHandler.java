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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 客户端处理心跳请求
 */
public class HeartbeatReqHandler extends ChannelInboundHandlerAdapter {

    private volatile ScheduledFuture<?> heartbeat;

    //    @Override
    //    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    //        System.out.println("heartbeat req channel active ...");
    //        heartbeat = ctx.executor().scheduleAtFixedRate(new HeartbeatTask(ctx), 0, 5, TimeUnit.SECONDS);
    //    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyMessage message = (NettyMessage) msg;
        if (message.getHeader() != null) {
            //heartbeat
            if (message.getHeader().getType() == Header.MessageType.HEARTBEAT_RESP.value) {
                System.out.println("[recv] client receive server heartbeat message ---> " + message);
            } else
                //heartbeat after receive handshake resp successfully.
                if (message.getHeader().getType() == Header.MessageType.HANDSHAKE_RESP.value) {
                    heartbeat = ctx.executor().scheduleAtFixedRate(new HeartbeatTask(ctx), 0, 5, TimeUnit.SECONDS);
                    System.out.println("[recv] client receive server login resp, start heartbeat ---> " + message);
                }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (null != heartbeat) {
            heartbeat.cancel(true);
            heartbeat = null;
        }
        cause.printStackTrace();
        ctx.fireExceptionCaught(cause);
    }

    private class HeartbeatTask implements Runnable {
        private final ChannelHandlerContext ctx;

        public HeartbeatTask(final ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            NettyMessage message = new NettyMessage();
            Header header = new Header();
            header.setType(Header.MessageType.HEARTBEAT_REQ.value);
            message.setHeader(header);
            message.setBody("heartbeat @" + System.currentTimeMillis() + " ...");
            System.out.println("[send] client send heartbeat to server ---> " + message);
            ctx.writeAndFlush(message);
        }
    }
}

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
package io.netty.example.socksproxy;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socks.SocksAuthResponse;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksAuthStatus;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdRequestDecoder;
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksRequest;


@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksRequest> {
    private static final String name = "SOCKS_SERVER_HANDLER";

    public static String getName() {
        return name;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        switch (socksRequest.requestType()) {
            case INIT: {
//                auth support example
//                ctx.pipeline().addFirst("socksAuthRequestDecoder",new SocksAuthRequestDecoder());
//                ctx.write(new SocksInitResponse(SocksMessage.SocksAuthScheme.AUTH_PASSWORD));
                ctx.pipeline().addFirst(SocksCmdRequestDecoder.getName(), new SocksCmdRequestDecoder());
                ctx.write(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
                break;
            }
            case AUTH:
                ctx.pipeline().addFirst(SocksCmdRequestDecoder.getName(), new SocksCmdRequestDecoder());
                ctx.write(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
                break;
            case CMD:
                SocksCmdRequest req = (SocksCmdRequest) socksRequest;
                if (req.cmdType() == SocksCmdType.CONNECT) {
                    ctx.pipeline().addLast(SocksServerConnectHandler.getName(), new SocksServerConnectHandler());
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(socksRequest);
                } else {
                    ctx.close();
                }
                break;
            case UNKNOWN:
                ctx.close();
                break;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) throws Exception {
        throwable.printStackTrace();
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}

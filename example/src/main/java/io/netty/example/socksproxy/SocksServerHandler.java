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
import io.netty.handler.codec.socksx.SocksRequest;
import io.netty.handler.codec.socksx.v4.Socks4CmdRequest;
import io.netty.handler.codec.socksx.v4.Socks4CmdType;
import io.netty.handler.codec.socksx.v5.Socks5AuthScheme;
import io.netty.handler.codec.socksx.v5.Socks5CmdRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitResponse;
import io.netty.handler.codec.socksx.v5.Socks5Request;
import io.netty.handler.codec.socksx.v5.Socks5AuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5CmdRequest;
import io.netty.handler.codec.socksx.v5.Socks5CmdType;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksRequest> {

    public static final SocksServerHandler INSTANCE = new SocksServerHandler();

    private SocksServerHandler() { }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        switch (socksRequest.protocolVersion()) {
            case SOCKS4a:
                Socks4CmdRequest socksV4CmdRequest = (Socks4CmdRequest) socksRequest;
                if (socksV4CmdRequest.cmdType() == Socks4CmdType.CONNECT) {
                    ctx.pipeline().addLast(new SocksServerConnectHandler());
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(socksRequest);
                } else {
                    ctx.close();
                }
                break;
            case SOCKS5:
                switch (((Socks5Request) socksRequest).requestType()) {
                    case INIT: {
                        // auth support example
                        //ctx.pipeline().addFirst(new SocksV5AuthRequestDecoder());
                        //ctx.write(new SocksV5InitResponse(SocksV5AuthScheme.AUTH_PASSWORD));
                        ctx.pipeline().addFirst(new Socks5CmdRequestDecoder());
                        ctx.write(new Socks5InitResponse(Socks5AuthScheme.NO_AUTH));
                        break;
                    }
                    case AUTH:
                        ctx.pipeline().addFirst(new Socks5CmdRequestDecoder());
                        ctx.write(new Socks5AuthResponse(Socks5AuthStatus.SUCCESS));
                        break;
                    case CMD:
                        Socks5CmdRequest socks5CmdRequest = (Socks5CmdRequest) socksRequest;
                        if (socks5CmdRequest.cmdType() == Socks5CmdType.CONNECT) {
                            ctx.pipeline().addLast(new SocksServerConnectHandler());
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
                break;
            case UNKNOWN:
                ctx.close();
                break;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        throwable.printStackTrace();
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}

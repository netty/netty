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
import io.netty.handler.codec.socksx.v4.SocksV4CmdRequest;
import io.netty.handler.codec.socksx.v4.SocksV4CmdType;
import io.netty.handler.codec.socksx.v5.SocksV5AuthScheme;
import io.netty.handler.codec.socksx.v5.SocksV5CmdRequestDecoder;
import io.netty.handler.codec.socksx.v5.SocksV5InitResponse;
import io.netty.handler.codec.socksx.v5.SocksV5Request;
import io.netty.handler.codec.socksx.v5.SocksV5AuthResponse;
import io.netty.handler.codec.socksx.v5.SocksV5AuthStatus;
import io.netty.handler.codec.socksx.v5.SocksV5CmdRequest;
import io.netty.handler.codec.socksx.v5.SocksV5CmdType;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksRequest> {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        switch (socksRequest.protocolVersion()) {
            case SOCKS4a:
                SocksV4CmdRequest socksV4CmdRequest = (SocksV4CmdRequest) socksRequest;
                if (socksV4CmdRequest.cmdType() == SocksV4CmdType.CONNECT) {
                    ctx.pipeline().addLast(new SocksServerConnectHandler());
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(socksRequest);
                } else {
                    ctx.close();
                }
                break;
            case SOCKS5:
                switch (((SocksV5Request) socksRequest).requestType()) {
                    case INIT: {
                        // auth support example
                        //ctx.pipeline().addFirst(new SocksV5AuthRequestDecoder());
                        //ctx.write(new SocksV5InitResponse(SocksV5AuthScheme.AUTH_PASSWORD));
                        ctx.pipeline().addFirst(new SocksV5CmdRequestDecoder());
                        ctx.write(new SocksV5InitResponse(SocksV5AuthScheme.NO_AUTH));
                        break;
                    }
                    case AUTH:
                        ctx.pipeline().addFirst(new SocksV5CmdRequestDecoder());
                        ctx.write(new SocksV5AuthResponse(SocksV5AuthStatus.SUCCESS));
                        break;
                    case CMD:
                        SocksV5CmdRequest socksV5CmdRequest = (SocksV5CmdRequest) socksRequest;
                        if (socksV5CmdRequest.cmdType() == SocksV5CmdType.CONNECT) {
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

    private static class SocksServerHandlerHolder {
        public static final SocksServerHandler HOLDER_INSTANCE = new SocksServerHandler();
    }

    public static SocksServerHandler getInstance() {
        return SocksServerHandlerHolder.HOLDER_INSTANCE;
    }
}

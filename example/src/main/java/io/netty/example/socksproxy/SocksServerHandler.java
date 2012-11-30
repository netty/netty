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
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.codec.socks.SocksCmdRequestDecoder;
import io.netty.codec.socks.SocksInitResponse;
import io.netty.codec.socks.SocksMessage;
import io.netty.codec.socks.SocksRequest;
import io.netty.codec.socks.SocksAuthResponse;
import io.netty.codec.socks.SocksCmdRequest;


@ChannelHandler.Sharable
public final class SocksServerHandler extends ChannelInboundMessageHandlerAdapter<SocksRequest> {
    private static final String name = "SOCKS_SERVER_HANDLER";

    public static String getName() {
        return name;
    }

    public SocksServerHandler() {
        super(SocksRequest.class);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        switch (socksRequest.getSocksRequestType()) {
            case INIT: {
//                auth support example
//                ctx.pipeline().addFirst("socksAuthRequestDecoder",new SocksAuthRequestDecoder());
//                ctx.write(new SocksInitResponse(SocksMessage.AuthScheme.AUTH_PASSWORD));
                ctx.pipeline().addFirst(SocksCmdRequestDecoder.getName(), new SocksCmdRequestDecoder());
                ctx.write(new SocksInitResponse(SocksMessage.AuthScheme.NO_AUTH));
                break;
            }
            case AUTH:
                ctx.pipeline().addFirst(SocksCmdRequestDecoder.getName(), new SocksCmdRequestDecoder());
                ctx.write(new SocksAuthResponse(SocksMessage.AuthStatus.SUCCESS));
                break;
            case CMD:
                SocksCmdRequest req = (SocksCmdRequest) socksRequest;
                if (req.getCmdType() == SocksMessage.CmdType.CONNECT) {
                    ctx.pipeline().addLast(SocksServerConnectHandler.getName(), new SocksServerConnectHandler());
                    ctx.pipeline().remove(this);
                    ctx.nextInboundMessageBuffer().add(socksRequest);
                    ctx.fireInboundBufferUpdated();
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
    public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable throwable) throws Exception {
        throwable.printStackTrace();
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}

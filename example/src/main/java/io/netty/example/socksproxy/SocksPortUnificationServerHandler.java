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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.socksx.SocksMessageEncoder;
import io.netty.handler.codec.socksx.SocksProtocolVersion;
import io.netty.handler.codec.socksx.v4.SocksV4CmdRequestDecoder;
import io.netty.handler.codec.socksx.v5.SocksV5InitRequestDecoder;

import java.util.List;

public class SocksPortUnificationServerHandler extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        SocksProtocolVersion version = SocksProtocolVersion.valueOf(in.readByte());
        System.out.println(version);
        in.resetReaderIndex();
        switch (version) {
            case SOCKS4a:
                p.addLast(new SocksV4CmdRequestDecoder());
                break;
            case SOCKS5:
                p.addLast(new SocksV5InitRequestDecoder());
                break;
            case UNKNOWN:
                in.clear();
                ctx.close();
                return;
        }
        p.addLast(SocksMessageEncoder.getInstance());
        p.addLast(SocksServerHandler.getInstance());
        p.remove(this);
    }
}

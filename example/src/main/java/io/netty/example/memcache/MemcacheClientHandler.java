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
package io.netty.example.memcache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheRequestHeader;
import io.netty.handler.codec.memcache.binary.DefaultBinaryMemcacheRequestHeader;
import io.netty.handler.codec.memcache.binary.DefaultFullBinaryMemcacheRequest;
import io.netty.handler.codec.memcache.binary.FullBinaryMemcacheRequest;
import io.netty.handler.codec.memcache.binary.FullBinaryMemcacheResponse;

import java.nio.charset.Charset;

public class MemcacheClientHandler extends ChannelHandlerAdapter {

    /**
     * Transforms basic string requests to binary memcache requests
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        String command = (String) msg;
        if (command.startsWith("get ")) {
            String key = command.substring("get ".length());
            BinaryMemcacheRequestHeader header = new DefaultBinaryMemcacheRequestHeader();
            header.setKeyLength((short) key.length());
            header.setTotalBodyLength(key.length());

            FullBinaryMemcacheRequest req = new DefaultFullBinaryMemcacheRequest(header, key, null);
            super.write(ctx, req, promise);
        } else if (command.startsWith("set ")) {
            String[] parts = command.split(" ", 3);
            String key = parts[1];
            String value = parts[2];

            BinaryMemcacheRequestHeader header = new DefaultBinaryMemcacheRequestHeader();

            header.setOpcode((byte) 1);
            header.setKeyLength((short) key.length());
            header.setExtrasLength((byte) 8);
            header.setTotalBodyLength(key.length() + 8 + value.length());

            ByteBuf content = Unpooled.wrappedBuffer(value.getBytes());
            ByteBuf extras = Unpooled.buffer(8, 8);
            extras.writeInt(0);
            extras.writeInt(0);
            FullBinaryMemcacheRequest req = new DefaultFullBinaryMemcacheRequest(header, key, extras, content);
            super.write(ctx, req, promise);
        } else {
            throw new IllegalStateException("unknown msg");
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        FullBinaryMemcacheResponse res = (FullBinaryMemcacheResponse) msg;
        System.out.println(res.content().toString(Charset.defaultCharset()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}

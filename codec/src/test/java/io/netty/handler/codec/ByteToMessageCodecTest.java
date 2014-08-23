/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;

import java.util.List;

public class ByteToMessageCodecTest {

    @Test(expected = IllegalStateException.class)
    public void testSharable() {
        new InvalidByteToMessageCodec();
    }

    @Test(expected = IllegalStateException.class)
    public void testSharable2() {
        new InvalidByteToMessageCodec2();
    }

    @ChannelHandler.Sharable
    private static final class InvalidByteToMessageCodec extends ByteToMessageCodec<Integer> {
        InvalidByteToMessageCodec() {
            super(true);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception { }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception { }
    }

    @ChannelHandler.Sharable
    private static final class InvalidByteToMessageCodec2 extends ByteToMessageCodec<Integer> {
        InvalidByteToMessageCodec2() {
            super(Integer.class, true);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception { }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception { }
    }
}

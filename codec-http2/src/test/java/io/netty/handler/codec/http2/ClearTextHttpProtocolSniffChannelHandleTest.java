/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClearTextHttpProtocolSniffChannelHandleTest {

    private static final ClearTextHttpProtocolSniffChannelHandle.ChannelConfigure doNothing =
            new ClearTextHttpProtocolSniffChannelHandle.ChannelConfigure() {
        @Override
        public void config(Channel channel) {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {

                @Override
                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {

                }

                @Override
                public boolean isSharable() {
                    return false;
                }
            });
        }
    };

    @Test
    public void testHttp2HappyPath() {
        final AtomicBoolean isHttp1 = new AtomicBoolean(false);
        final AtomicBoolean isHttp2 = new AtomicBoolean(false);

        final DownstreamChannelHandle downstreamChannelHandle = new DownstreamChannelHandle();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline().addFirst(initSniffHandler(isHttp1, isHttp2, downstreamChannelHandle));

        embeddedChannel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());

        Assertions.assertTrue(isHttp2.get());
        Assertions.assertFalse(isHttp1.get());

        Assertions.assertNotNull(downstreamChannelHandle.bufFromUpstream);
        Assertions.assertTrue(
                ByteBufUtil.equals(Http2CodecUtil.connectionPrefaceBuf(), downstreamChannelHandle.bufFromUpstream)
        );

        ClearTextHttpProtocolSniffChannelHandle httpProtocolSniffChannelHandle =
                embeddedChannel.pipeline().get(ClearTextHttpProtocolSniffChannelHandle.class);

        Assertions.assertNull(httpProtocolSniffChannelHandle);
        downstreamChannelHandle.release();
    }

    @Test
    public void testHttp2BadPath() {
        final AtomicBoolean isHttp1 = new AtomicBoolean(false);
        final AtomicBoolean isHttp2 = new AtomicBoolean(false);

        final DownstreamChannelHandle downstreamChannelHandle = new DownstreamChannelHandle();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline().addFirst(initSniffHandler(isHttp1, isHttp2, downstreamChannelHandle));

        ByteBuf httpPreface = Http2CodecUtil.connectionPrefaceBuf();
        ByteBuf part0 = httpPreface.slice(0, 10);
        ByteBuf part1 = httpPreface.slice(10, httpPreface.readableBytes() - part0.readableBytes());

        embeddedChannel.writeInbound(part0);

        // The protocol has not been determined yet.
        Assertions.assertFalse(isHttp2.get());
        Assertions.assertFalse(isHttp1.get());
        Assertions.assertNull(downstreamChannelHandle.bufFromUpstream);
        Assertions.assertNotNull(embeddedChannel.pipeline().get(ClearTextHttpProtocolSniffChannelHandle.class));

        embeddedChannel.writeInbound(part1);
        // The protocol has been determined.
        Assertions.assertTrue(isHttp2.get());
        Assertions.assertFalse(isHttp1.get());
        Assertions.assertNotNull(downstreamChannelHandle.bufFromUpstream);
        Assertions.assertNull(embeddedChannel.pipeline().get(ClearTextHttpProtocolSniffChannelHandle.class));
        Assertions.assertTrue(
                ByteBufUtil.equals(Http2CodecUtil.connectionPrefaceBuf(), downstreamChannelHandle.bufFromUpstream)
        );
        downstreamChannelHandle.release();
    }

    @Test
    public void testHttp1HappyPath() {
        AtomicBoolean isHttp1 = new AtomicBoolean(false);
        AtomicBoolean isHttp2 = new AtomicBoolean(false);

        DownstreamChannelHandle downstreamChannelHandle = new DownstreamChannelHandle();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline().addFirst(initSniffHandler(isHttp1, isHttp2, downstreamChannelHandle));

        ByteBuf http1RequestLine = Unpooled.directBuffer();
        http1RequestLine.writeBytes("GET https://netty.io/wiki/index.html HTTP/1.1".getBytes(Charset.defaultCharset()));
        embeddedChannel.writeInbound(http1RequestLine);

        Assertions.assertFalse(isHttp2.get());
        Assertions.assertTrue(isHttp1.get());
        Assertions.assertTrue(
                ByteBufUtil.equals(http1RequestLine, downstreamChannelHandle.bufFromUpstream)
        );
        downstreamChannelHandle.release();
    }

    @Test
    public void testHttp1BadPath() {
        AtomicBoolean isHttp1 = new AtomicBoolean(false);
        AtomicBoolean isHttp2 = new AtomicBoolean(false);

        DownstreamChannelHandle downstreamChannelHandle = new DownstreamChannelHandle();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline().addFirst(initSniffHandler(isHttp1, isHttp2, downstreamChannelHandle));

        ByteBuf http1RequestLine = Unpooled.directBuffer();
        http1RequestLine.writeBytes("GET https://netty.io/wiki/index.html HTTP/1.1".getBytes(Charset.defaultCharset()));

        ByteBuf part0 = http1RequestLine.retainedSlice(0, 10);
        ByteBuf part1 = http1RequestLine.retainedSlice(10, http1RequestLine.readableBytes() - part0.readableBytes());

        embeddedChannel.writeInbound(part0);

        // The protocol has not been determined yet.
        Assertions.assertFalse(isHttp2.get());
        Assertions.assertFalse(isHttp1.get());
        Assertions.assertNull(downstreamChannelHandle.bufFromUpstream);
        Assertions.assertNotNull(embeddedChannel.pipeline().get(ClearTextHttpProtocolSniffChannelHandle.class));

        embeddedChannel.writeInbound(part1);
        // The protocol has been determined.
        Assertions.assertFalse(isHttp2.get());
        Assertions.assertTrue(isHttp1.get());
        Assertions.assertTrue(ByteBufUtil.equals(http1RequestLine, downstreamChannelHandle.bufFromUpstream));
        Assertions.assertNull(embeddedChannel.pipeline().get(ClearTextHttpProtocolSniffChannelHandle.class));
        downstreamChannelHandle.release();
        http1RequestLine.release();
    }

    @Test
    public void overflow() {
        DownstreamMergeChannelHandle downstreamChannelHandle = new DownstreamMergeChannelHandle();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline()
                .addFirst(initSniffHandler(new AtomicBoolean(), new AtomicBoolean(), downstreamChannelHandle));
        String testSequence = "1234567890";
        StringBuilder resultSequence = new StringBuilder();
        int count = 10;
        for (int i = 0; i < count; i++) {
            ByteBuf part = Unpooled.directBuffer();
            part.writeBytes(testSequence.getBytes(Charset.defaultCharset()));
            embeddedChannel.writeInbound(part);
            resultSequence.append(testSequence);
        }

        CompositeByteBuf compositeByteBuf = downstreamChannelHandle.compositeByteBuf;
        Assertions.assertEquals(count * testSequence.length(), compositeByteBuf.readableBytes());
        Assertions.assertTrue(ByteBufUtil.equals(
                Unpooled.wrappedBuffer(resultSequence.toString().getBytes()),
                compositeByteBuf
        ));
        downstreamChannelHandle.release();
    }

    @Test
    public void testByteRelease() {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline().addFirst(
                new ClearTextHttpProtocolSniffChannelHandle(
                        doNothing, doNothing
                ));
        String testSequence = "1234567890";
        ByteBuf part = Unpooled.directBuffer();
        part.writeBytes(testSequence.getBytes(Charset.defaultCharset()));

        embeddedChannel.writeInbound(part);

        Assertions.assertNotNull(embeddedChannel.pipeline().get(ClearTextHttpProtocolSniffChannelHandle.class));

        embeddedChannel.close();
        Assertions.assertEquals(0, part.refCnt());
    }

    @Test
    public void testSetNull() throws NoSuchFieldException, IllegalAccessException {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();

        ClearTextHttpProtocolSniffChannelHandle channelHandle = new ClearTextHttpProtocolSniffChannelHandle(
                doNothing, doNothing
        );
        embeddedChannel.pipeline().addFirst(channelHandle);

        embeddedChannel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());

        // Let`s close channel and unregister all channel handles
        embeddedChannel.close();
        Field field = ClearTextHttpProtocolSniffChannelHandle.class.getDeclaredField("compositeByteBuf");
        field.setAccessible(true);
        Assertions.assertNull(field.get(channelHandle));
    }

    private ClearTextHttpProtocolSniffChannelHandle initSniffHandler(
            final AtomicBoolean isHttp1, final AtomicBoolean isHttp2,
            final ChannelInboundHandlerAdapter downstreamHandler
    ) {
        return new ClearTextHttpProtocolSniffChannelHandle(
                new ClearTextHttpProtocolSniffChannelHandle.ChannelConfigure() {
                    @Override
                    public void config(Channel channel) {
                        isHttp1.set(true);
                        channel.pipeline().addLast(downstreamHandler);
                    }
                },
                new ClearTextHttpProtocolSniffChannelHandle.ChannelConfigure() {
                    @Override
                    public void config(Channel channel) {
                        isHttp2.set(true);
                        channel.pipeline().addLast(downstreamHandler);
                    }
                }
        );
    }

    static class DownstreamChannelHandle extends ChannelInboundHandlerAdapter {
        private ByteBuf bufFromUpstream;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                this.bufFromUpstream = (ByteBuf) msg;
            }
        }

        public void release() {
            if (bufFromUpstream != null) {
                bufFromUpstream.release();
            }
        }
    }

    static class DownstreamMergeChannelHandle extends ChannelInboundHandlerAdapter {
        private CompositeByteBuf compositeByteBuf;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (compositeByteBuf == null) {
                compositeByteBuf = ctx.alloc().compositeBuffer();
            }
            if (msg instanceof ByteBuf) {
                compositeByteBuf.addComponents(true, (ByteBuf) msg);
            }
        }

        public void release() {
            if (compositeByteBuf != null) {
                compositeByteBuf.release();
            }
        }
    }


}

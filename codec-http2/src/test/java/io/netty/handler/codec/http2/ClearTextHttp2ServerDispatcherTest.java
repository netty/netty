/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AsciiString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link ClearTextHttp2ServerDispatcher}
 */
public class ClearTextHttp2ServerDispatcherTest {
    private EmbeddedChannel channel;

    private List<ByteBuf> upgradeInbounds;

    private ChannelInboundHandler upgradeHandler;

    private List<ByteBuf> priorKnowledgeInbounds;

    private ChannelInboundHandler priorKnowledgeHandler;

    @Before
    public void setUp() throws Exception {
        upgradeInbounds = new ArrayList<ByteBuf>();
        upgradeHandler = new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                upgradeInbounds.add(msg.retain());
            }
        };

        priorKnowledgeInbounds = new ArrayList<ByteBuf>();
        priorKnowledgeHandler = new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                priorKnowledgeInbounds.add(msg.retain());
            }
        };

        ClearTextHttp2ServerDispatcher dispatcher = new ClearTextHttp2ServerDispatcher() {
            @Override
            protected void configureUpgrade(ChannelHandlerContext ctx) {
                ctx.pipeline().addLast(upgradeHandler);
            }

            @Override
            protected void configurePriorKnowledge(ChannelHandlerContext ctx) {
                ctx.pipeline().addLast(priorKnowledgeHandler);
            }
        };

        channel = new EmbeddedChannel();
        channel.connect(new InetSocketAddress(0));
        channel.pipeline().addLast(dispatcher);
    }

    @After
    public void tearDown() {
        channel.finishAndReleaseAll();

        for (ByteBuf byteBuf : upgradeInbounds) {
            byteBuf.release();
        }

        for (ByteBuf byteBuf : priorKnowledgeInbounds) {
            byteBuf.release();
        }
    }

    @Test
    public void priorKnowledge() throws Exception {
        ByteBuf firstInbound = Unpooled.directBuffer().writeBytes(
                AsciiString.of("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n").array());
        ByteBuf secondInbound = Unpooled.directBuffer().writeBytes(
                AsciiString.of("This is payload").array());

        try {
            channel.writeInbound(firstInbound.copy());
            channel.writeInbound(secondInbound.copy());

            assertTrue(upgradeInbounds.isEmpty());

            assertEquals(2, priorKnowledgeInbounds.size());
            assertEquals(firstInbound, priorKnowledgeInbounds.get(0));
            assertEquals(secondInbound, priorKnowledgeInbounds.get(1));
        } finally {
            firstInbound.release();
            secondInbound.release();
        }
    }

    @Test
    public void upgrade() throws Exception {
        ByteBuf firstInbound = Unpooled.directBuffer().writeBytes(
                AsciiString.of("GET / HTTP/1.1").array());
        ByteBuf secondInbound = Unpooled.directBuffer().writeBytes(
                AsciiString.of("This is payload").array());

        try {
            channel.writeInbound(firstInbound.copy());
            channel.writeInbound(secondInbound.copy());

            assertTrue(priorKnowledgeInbounds.isEmpty());

            assertEquals(2, upgradeInbounds.size());
            assertEquals(firstInbound, upgradeInbounds.get(0));
            assertEquals(secondInbound, upgradeInbounds.get(1));
        } finally {
            firstInbound.release();
            secondInbound.release();
        }
    }

    @Test
    public void priorKnowledgeWhenFirstInboundNotEnough() throws Exception {
        ByteBuf inbound = Unpooled.directBuffer().writeBytes(
                AsciiString.of("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n").array());

        try {
            ByteBuf copied = inbound.copy();
            channel.writeInbound(copied.readBytes(2), copied);

            assertTrue(upgradeInbounds.isEmpty());

            assertEquals(1, priorKnowledgeInbounds.size());
            assertEquals(inbound, priorKnowledgeInbounds.get(0));
        } finally {
            inbound.release();
        }
    }

    @Test
    public void ignoreNonByteBuf() throws Exception {
        ByteBuf inbound = Unpooled.directBuffer().writeBytes(
                AsciiString.of("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n").array());

        try {
            channel.writeInbound(new Http2Settings(), inbound.copy());

            assertTrue(upgradeInbounds.isEmpty());

            assertEquals(1, priorKnowledgeInbounds.size());
            assertEquals(inbound, priorKnowledgeInbounds.get(0));
        } finally {
            inbound.release();
        }
    }
}

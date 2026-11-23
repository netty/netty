/*
 * Copyright 2026 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryDecoder;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A query must only fall back to TCP once, even if the nameserver's truncated UDP response is received more than
 * once, or is received after a timeout already started the fallback.
 */
public class DnsTruncatedTcpFallbackTest {

    private static final DnsQuestion QUESTION = new DefaultDnsQuestion("somehost.netty.io.", DnsRecordType.A);

    private final BlockingQueue<DatagramDnsQuery> udpQueries = new LinkedBlockingQueue<DatagramDnsQuery>();
    private final BlockingQueue<TcpQuery> tcpQueries = new LinkedBlockingQueue<TcpQuery>();
    private final AtomicInteger tcpConnections = new AtomicInteger();
    private EventLoopGroup serverGroup;
    private EventLoopGroup group;
    private Channel udpServer;
    private Channel tcpServer;

    @BeforeEach
    public void setUp() throws Exception {
        serverGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        udpServer = new Bootstrap().group(serverGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new DatagramDnsQueryDecoder(), new DatagramDnsResponseEncoder(),
                                new SimpleChannelInboundHandler<DatagramDnsQuery>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
                                        udpQueries.add(query.retain());
                                    }
                                });
                    }
                }).bind(NetUtil.LOCALHOST4, 0).sync().channel();
        // TCP listens on the same port as UDP, like a real nameserver.
        tcpServer = new ServerBootstrap().group(serverGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new TcpDnsQueryDecoder(), new TcpDnsResponseEncoder(),
                                new SimpleChannelInboundHandler<DnsQuery>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, DnsQuery query) {
                                        tcpQueries.add(new TcpQuery(ctx.channel(), query.retain()));
                                    }
                                });
                    }
                }).bind(udpServer.localAddress()).awaitUninterruptibly().channel();
        Assumptions.assumeTrue(tcpServer.isActive(), "Unable to bind TCP to " + udpServer.localAddress());
    }

    @AfterEach
    public void tearDown() throws Exception {
        for (DatagramDnsQuery query; (query = udpQueries.poll()) != null;) {
            query.release();
        }
        for (TcpQuery query; (query = tcpQueries.poll()) != null;) {
            query.query.release();
        }
        if (udpServer != null) {
            udpServer.close().sync();
        }
        if (tcpServer != null) {
            tcpServer.close().sync();
        }
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        serverGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
    }

    @Test
    @Timeout(30)
    public void testDuplicateTruncatedResponseFallsBackToTcpOnce() throws Exception {
        CountDownLatch udpResponsesRead = new CountDownLatch(2);
        DnsNameResolver resolver = newResolver(udpResponsesRead, 10000, false);
        try {
            Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future = resolver.query(QUESTION);
            DatagramDnsQuery udpQuery = udpQueries.poll(10, TimeUnit.SECONDS);
            assertThat(udpQuery).isNotNull();
            try {
                // The nameserver's truncated response is received twice.
                udpServer.writeAndFlush(truncated(udpQuery));
                udpServer.writeAndFlush(truncated(udpQuery));
            } finally {
                udpQuery.release();
            }
            assertThat(udpResponsesRead.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(tcpConnections.get()).isEqualTo(1);

            answerTcpQueryAndCheckResult(future);
        } finally {
            resolver.close();
        }
    }

    @Test
    @Timeout(30)
    public void testTruncatedResponseAfterTimeoutFallsBackToTcpOnce() throws Exception {
        CountDownLatch udpResponsesRead = new CountDownLatch(1);
        DnsNameResolver resolver = newResolver(udpResponsesRead, 2000, true);
        try {
            Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future = resolver.query(QUESTION);
            DatagramDnsQuery udpQuery = udpQueries.poll(10, TimeUnit.SECONDS);
            assertThat(udpQuery).isNotNull();
            try {
                // Don't answer via UDP until the query timed out and was retried via TCP.
                TcpQuery tcpQuery = tcpQueries.poll(10, TimeUnit.SECONDS);
                assertThat(tcpQuery).isNotNull();
                tcpQueries.add(tcpQuery);
                assertThat(tcpConnections.get()).isEqualTo(1);

                // The truncated UDP response arrives late.
                udpServer.writeAndFlush(truncated(udpQuery));
            } finally {
                udpQuery.release();
            }
            assertThat(udpResponsesRead.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(tcpConnections.get()).isEqualTo(1);

            answerTcpQueryAndCheckResult(future);
        } finally {
            resolver.close();
        }
    }

    @Test
    @Timeout(30)
    public void testTimeoutAfterTruncatedResponseDoesNotFallBackToTcpAgain() throws Exception {
        CountDownLatch udpResponsesRead = new CountDownLatch(1);
        DnsNameResolver resolver = newResolver(udpResponsesRead, 2000, true);
        try {
            Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future = resolver.query(QUESTION);
            DatagramDnsQuery udpQuery = udpQueries.poll(10, TimeUnit.SECONDS);
            assertThat(udpQuery).isNotNull();
            try {
                udpServer.writeAndFlush(truncated(udpQuery));
            } finally {
                udpQuery.release();
            }
            assertThat(udpResponsesRead.await(10, TimeUnit.SECONDS)).isTrue();

            // Never answer the TCP query. The timeout of the UDP query is scheduled before the timeout of the TCP
            // query, so it already fired once the future completes because of the TCP timeout.
            TcpQuery tcpQuery = tcpQueries.poll(10, TimeUnit.SECONDS);
            assertThat(tcpQuery).isNotNull();
            tcpQuery.query.release();
            future.awaitUninterruptibly();
            if (future.isSuccess()) {
                // The TCP fallback failed, so the truncated response is used.
                future.getNow().release();
            }
            assertThat(tcpConnections.get()).isEqualTo(1);
        } finally {
            resolver.close();
        }
    }

    private DnsNameResolver newResolver(final CountDownLatch udpResponsesRead, long queryTimeoutMillis,
                                        boolean retryOnTimeout) {
        return new DnsNameResolverBuilder(group.next())
                .datagramChannelFactory(() -> {
                    NioDatagramChannel ch = new NioDatagramChannel();
                    // Counts the responses once the resolver has processed them.
                    ch.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ctx.fireChannelRead(msg);
                            udpResponsesRead.countDown();
                        }
                    });
                    return ch;
                })
                .socketChannelFactory(() -> {
                    tcpConnections.incrementAndGet();
                    return new NioSocketChannel();
                }, retryOnTimeout)
                .queryTimeoutMillis(queryTimeoutMillis)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(
                        (InetSocketAddress) udpServer.localAddress()))
                .build();
    }

    private void answerTcpQueryAndCheckResult(Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future)
            throws Exception {
        TcpQuery tcpQuery = tcpQueries.poll(10, TimeUnit.SECONDS);
        assertThat(tcpQuery).isNotNull();
        try {
            tcpQuery.channel.writeAndFlush(answer(tcpQuery.query));
        } finally {
            tcpQuery.query.release();
        }
        AddressedEnvelope<DnsResponse, InetSocketAddress> envelope = future.sync().getNow();
        try {
            assertThat(envelope.content().isTruncated()).isFalse();
            DnsRawRecord record = envelope.content().recordAt(DnsSection.ANSWER, 0);
            assertThat(record.type()).isEqualTo(DnsRecordType.A);
            assertThat(record.content().readableBytes()).isEqualTo(4);
        } finally {
            envelope.release();
        }
        assertThat(tcpQueries).isEmpty();
        assertThat(tcpConnections.get()).isEqualTo(1);
    }

    private static DatagramDnsResponse truncated(DatagramDnsQuery query) {
        DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
        response.setTruncated(true);
        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(question.name(), question.type()));
        return response;
    }

    private static DnsResponse answer(DnsQuery query) {
        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        DnsResponse response = new DefaultDnsResponse(query.id());
        response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(question.name(), question.type()));
        response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(question.name(), DnsRecordType.A, 60,
                Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 4 })));
        return response;
    }

    private static final class TcpQuery {
        final Channel channel;
        final DnsQuery query;

        TcpQuery(Channel channel, DnsQuery query) {
            this.channel = channel;
            this.query = query;
        }
    }
}

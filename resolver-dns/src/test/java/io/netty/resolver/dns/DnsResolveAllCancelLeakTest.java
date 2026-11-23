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
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code resolveAll(DnsQuestion, ...)} retains the records it collects. If the caller's promise is cancelled while a
 * CNAME follow-up query is in flight, the listener of the follow-up query returns early and
 * {@code finishResolve(...)} is never called, so the records collected so far must be released there.
 */
public class DnsResolveAllCancelLeakTest {

    private static final String FOLLOW_UP_NAME = "other.example.com.";

    @Test
    @Timeout(30)
    public void testCancelWhileCnameFollowUpInFlightReleasesRecords() throws Exception {
        final CountDownLatch followUpArrived = new CountDownLatch(1);
        final CountDownLatch answerFollowUp = new CountDownLatch(1);
        final CountDownLatch followUpCancelled = new CountDownLatch(1);
        EventLoopGroup serverGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            Channel server = new Bootstrap().group(serverGroup)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        protected void initChannel(DatagramChannel ch) {
                            ch.pipeline().addLast(new DatagramDnsQueryDecoder(), new DatagramDnsResponseEncoder(),
                                    new CnameAnsweringHandler(followUpArrived, answerFollowUp));
                        }
                    }).bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();

            EventLoop loop = group.next();
            final TrackingAllocator allocator = new TrackingAllocator();
            DnsNameResolver resolver = new DnsNameResolverBuilder(loop)
                    .datagramChannelFactory(() -> {
                        NioDatagramChannel ch = new NioDatagramChannel();
                        ch.config().setAllocator(allocator);
                        return ch;
                    })
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(
                            (InetSocketAddress) server.localAddress()))
                    .searchDomains(Collections.<String>emptyList())
                    .optResourceEnabled(false)
                    .queryTimeoutMillis(10000)
                    .dnsQueryLifecycleObserverFactory(
                            question -> new FollowUpCancelledObserver(question, followUpCancelled))
                    .build();
            try {
                final Promise<List<DnsRecord>> promise = loop.newPromise();
                loop.submit(() -> resolver.resolveAll(new DefaultDnsQuestion("example.com.", DnsRecordType.TXT),
                        Collections.<DnsRecord>emptyList(), promise)).sync();

                // The TXT record of the first response was collected and the CNAME follow-up query is in flight.
                assertThat(followUpArrived.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(promise.cancel(false)).isTrue();
                answerFollowUp.countDown();

                // The follow-up response arrives after the cancellation and its listener returns early.
                assertThat(followUpCancelled.await(10, TimeUnit.SECONDS)).isTrue();
                // Make sure the listener has returned.
                loop.submit(() -> { }).sync();
                assertThat(allocator.stillReferenced()).as("DNS response buffers still referenced").isZero();
            } finally {
                resolver.close();
            }
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
            serverGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    /**
     * Answers {@code example.com.} with a TXT record and a CNAME to {@link #FOLLOW_UP_NAME}, and the follow-up query
     * with a TXT record once {@code answerFollowUp} is counted down.
     */
    private static final class CnameAnsweringHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {
        private final CountDownLatch followUpArrived;
        private final CountDownLatch answerFollowUp;

        CnameAnsweringHandler(CountDownLatch followUpArrived, CountDownLatch answerFollowUp) {
            this.followUpArrived = followUpArrived;
            this.answerFollowUp = answerFollowUp;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) throws Exception {
            DnsQuestion question = query.recordAt(DnsSection.QUESTION);
            boolean followUp = FOLLOW_UP_NAME.equals(question.name());
            if (followUp) {
                followUpArrived.countDown();
                answerFollowUp.await(10, TimeUnit.SECONDS);
            }
            DatagramDnsResponse response = new DatagramDnsResponse(
                    query.recipient(), query.sender(), query.id(), query.opCode(), DnsResponseCode.NOERROR);
            response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(question.name(), question.type()));
            response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(question.name(), DnsRecordType.TXT, 60,
                    Unpooled.wrappedBuffer(new byte[] { 3, 'f', 'o', 'o' })));
            if (!followUp) {
                response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(question.name(),
                        DnsRecordType.CNAME, 60, encodeName(FOLLOW_UP_NAME)));
            }
            ctx.writeAndFlush(response);
        }

        private static ByteBuf encodeName(String name) {
            ByteBuf buf = Unpooled.buffer();
            for (String label : name.split("\\.")) {
                buf.writeByte(label.length());
                buf.writeCharSequence(label, StandardCharsets.US_ASCII);
            }
            return buf.writeByte(0);
        }
    }

    /**
     * Counts down {@code followUpCancelled} when the query for {@link #FOLLOW_UP_NAME} is cancelled, which happens when
     * its listener sees that the promise of the resolution is done already.
     */
    private static final class FollowUpCancelledObserver implements DnsQueryLifecycleObserver {
        private final DnsQuestion question;
        private final CountDownLatch followUpCancelled;

        FollowUpCancelledObserver(DnsQuestion question, CountDownLatch followUpCancelled) {
            this.question = question;
            this.followUpCancelled = followUpCancelled;
        }

        @Override
        public void queryWritten(InetSocketAddress dnsServerAddress, ChannelFuture future) {
        }

        @Override
        public void queryCancelled(int queriesRemaining) {
            if (FOLLOW_UP_NAME.equals(question.name())) {
                followUpCancelled.countDown();
            }
        }

        @Override
        public DnsQueryLifecycleObserver queryRedirected(List<InetSocketAddress> nameServers) {
            return this;
        }

        @Override
        public DnsQueryLifecycleObserver queryCNAMEd(DnsQuestion cnameQuestion) {
            return new FollowUpCancelledObserver(cnameQuestion, followUpCancelled);
        }

        @Override
        public DnsQueryLifecycleObserver queryNoAnswer(DnsResponseCode code) {
            return this;
        }

        @Override
        public void queryFailed(Throwable cause) {
        }

        @Override
        public void querySucceed() {
        }
    }

    /**
     * Keeps every buffer the resolver channel allocates, so the test can check that all of them were released.
     */
    private static final class TrackingAllocator extends AbstractByteBufAllocator {
        private final List<ByteBuf> buffers = Collections.synchronizedList(new ArrayList<ByteBuf>());

        TrackingAllocator() {
            super(true);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return false;
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            ByteBuf buf = Unpooled.directBuffer(initialCapacity, maxCapacity);
            buffers.add(buf);
            return buf;
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            ByteBuf buf = Unpooled.buffer(initialCapacity, maxCapacity);
            buffers.add(buf);
            return buf;
        }

        int stillReferenced() {
            int live = 0;
            synchronized (buffers) {
                for (ByteBuf buf : buffers) {
                    if (buf.refCnt() > 0) {
                        live++;
                    }
                }
            }
            return live;
        }
    }
}

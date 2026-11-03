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
package io.netty.handler.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that {@link SslHandler} does not encrypt more than the write buffer high water mark ahead of what the
 * transport has written, even though the plaintext it queues makes the channel unwritable.
 */
public class SslHandlerWrapBackpressureTest {

    private static final int HIGH_WATER_MARK = 32 * 1024;
    /** Once the high water mark is exceeded at most one more record, with up to 16KB of plaintext, is wrapped. */
    private static final int MAX_HELD_BYTES = HIGH_WATER_MARK + 16 * 1024 + 1024;
    private static final int DATA_LENGTH = 1024 * 1024;
    private static final int APPLICATION_DATA = 23;

    @ParameterizedTest
    @EnumSource(SslProvider.class)
    @Timeout(30)
    public void testWrapIsSuspendedUntilEncryptedDataIsWritten(SslProvider provider) throws Exception {
        try (Peers peers = new Peers(provider)) {
            byte[] data = newData();
            ChannelFuture first = peers.client.writeAndFlush(Unpooled.wrappedBuffer(data, 0, DATA_LENGTH / 2));
            // The plaintext queued in the SslHandler makes the channel unwritable, which must not stop the
            // SslHandler from wrapping it.
            assertThat(peers.client.isWritable()).isFalse();
            int heldBytes = peers.holder.heldBytes;
            assertThat(heldBytes).isPositive().isLessThanOrEqualTo(MAX_HELD_BYTES);

            // As long as the encrypted data is not written, flushing again must not wrap more data.
            ChannelFuture second = peers.client.writeAndFlush(
                    Unpooled.wrappedBuffer(data, DATA_LENGTH / 2, DATA_LENGTH / 2));
            assertThat(peers.holder.heldBytes).isEqualTo(heldBytes);

            int rounds = 0;
            while (!second.isDone()) {
                assertThat(peers.holder.heldBytes).isPositive().isLessThanOrEqualTo(MAX_HELD_BYTES);
                // Writing the encrypted data resumes wrapping.
                peers.holder.release();
                rounds++;
            }
            assertThat(first.isSuccess()).isTrue();
            assertThat(second.isSuccess()).isTrue();
            assertThat(rounds).isGreaterThanOrEqualTo(DATA_LENGTH / MAX_HELD_BYTES);
            peers.client.checkException();

            peers.transferToServer();
            peers.assertServerReceived(data);
        }
    }

    @ParameterizedTest
    @EnumSource(SslProvider.class)
    @Timeout(30)
    public void testCloseOutboundWrapsSuspendedData(SslProvider provider) throws Exception {
        testCloseWrapsSuspendedData(provider, false);
    }

    @ParameterizedTest
    @EnumSource(SslProvider.class)
    @Timeout(30)
    public void testCloseWrapsSuspendedData(SslProvider provider) throws Exception {
        testCloseWrapsSuspendedData(provider, true);
    }

    private static void testCloseWrapsSuspendedData(SslProvider provider, boolean closeChannel) throws Exception {
        try (Peers peers = new Peers(provider)) {
            byte[] data = newData();
            ChannelFuture writeFuture = peers.client.writeAndFlush(Unpooled.wrappedBuffer(data));
            assertThat(peers.holder.heldBytes).isPositive().isLessThanOrEqualTo(MAX_HELD_BYTES);

            ChannelFuture closeFuture = closeChannel ? peers.client.close() : peers.clientSslHandler.closeOutbound();
            // All the data must be wrapped before the outbound side of the engine is closed, as the data would be
            // failed otherwise.
            assertThat(peers.holder.heldBytes).isGreaterThan(DATA_LENGTH);
            peers.holder.release();
            assertThat(writeFuture.isSuccess()).as(String.valueOf(writeFuture.cause())).isTrue();
            assertThat(closeFuture.isSuccess()).as(String.valueOf(closeFuture.cause())).isTrue();

            peers.transferToServer();
            peers.assertServerReceived(data);
            // The close_notify was received after all the data.
            assertThat(peers.serverSslHandler.sslCloseFuture().isSuccess()).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(SslProvider.class)
    @Timeout(30)
    public void testWrapIsResumedOnceLowWaterMarkIsReached(SslProvider provider) throws Exception {
        try (Peers peers = new Peers(provider)) {
            final int lowWaterMark = 40 * 1024;
            final int highWaterMark = 64 * 1024;
            peers.client.config().setWriteBufferWaterMark(new WriteBufferWaterMark(lowWaterMark, highWaterMark));
            // Complete the writes one by one, like a transport that can only write some of the data at once.
            WriteHoldingHandler writeHolder = new WriteHoldingHandler();
            peers.client.pipeline().remove(peers.holder);
            peers.client.pipeline().addFirst(writeHolder);

            byte[] data = newData();
            ChannelFuture writeFuture = peers.client.writeAndFlush(Unpooled.wrappedBuffer(data));
            assertThat(writeHolder.heldBytes).isGreaterThan(highWaterMark).isLessThanOrEqualTo(
                    highWaterMark + MAX_HELD_BYTES - HIGH_WATER_MARK);

            // Like the writability of the channel, wrapping continues once not more than the low water mark is
            // pending anymore, which is before everything was written.
            int writes = writeHolder.heldBytesOnWrite.size();
            while (writeHolder.heldBytesOnWrite.size() == writes) {
                writeHolder.releaseOldest();
            }
            assertThat(writeHolder.heldBytesOnWrite.get(writes)).isPositive().isLessThanOrEqualTo(lowWaterMark);

            while (!writeFuture.isDone()) {
                writeHolder.releaseOldest();
            }
            assertThat(writeFuture.isSuccess()).as(String.valueOf(writeFuture.cause())).isTrue();
            peers.client.pipeline().remove(writeHolder);
            peers.transferToServer();
            peers.assertServerReceived(data);
        }
    }

    @ParameterizedTest
    @EnumSource(SslProvider.class)
    @Timeout(30)
    public void testFailedWriteFailsSuspendedData(SslProvider provider) throws Exception {
        try (Peers peers = new Peers(provider)) {
            ChannelFuture writeFuture = peers.client.writeAndFlush(Unpooled.wrappedBuffer(newData()));
            int heldBytes = peers.holder.heldBytes;
            assertThat(heldBytes).isPositive().isLessThanOrEqualTo(MAX_HELD_BYTES);

            // Close the transport without going through the pipeline, which fails the writes that are pending.
            peers.client.unsafe().close(peers.client.voidPromise());
            // What was not wrapped yet is failed as well, without wrapping it first.
            assertThat(writeFuture.cause()).isInstanceOf(ClosedChannelException.class);
            assertThat(peers.holder.heldBytes).isEqualTo(heldBytes);
        }
    }

    private static byte[] newData() {
        byte[] data = new byte[DATA_LENGTH];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    @Test
    @Timeout(30)
    public void testWrapIsResumedOnceDelegatedTaskCompleted() throws Exception {
        try (Peers peers = new Peers(SslProvider.JDK, SslProtocols.TLS_v1_2, new ManualExecutor())) {
            byte[] data = newData();
            ChannelFuture writeFuture = peers.client.writeAndFlush(Unpooled.wrappedBuffer(data));
            assertThat(peers.holder.appDataBytes).isPositive().isLessThanOrEqualTo(MAX_HELD_BYTES);

            // The server starts a renegotiation, and the client processes the HelloRequest in a delegated task.
            peers.serverSslHandler.renegotiate();
            peers.transferToClient();
            assertThat(peers.executor.hasTasks()).isTrue();

            // The encrypted data is written while the task runs, but we must not wrap before the task completed.
            peers.holder.release();
            assertThat(peers.holder.appDataBytes).isZero();
            peers.runTasks();
            assertThat(peers.holder.appDataBytes).isPositive().isLessThanOrEqualTo(MAX_HELD_BYTES);

            peers.exchangeUntilDone(writeFuture);
            assertThat(writeFuture.isSuccess()).as(String.valueOf(writeFuture.cause())).isTrue();
            peers.assertServerReceived(data);
        }
    }

    @ParameterizedTest(name = "{index}: closeChannel={0}, taskRunning={1}")
    @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
    @Timeout(30)
    public void testCloseDuringRenegotiationWrapsSuspendedData(boolean closeChannel, boolean taskRunning)
            throws Exception {
        try (Peers peers = new Peers(SslProvider.JDK, SslProtocols.TLS_v1_2,
                taskRunning ? new ManualExecutor() : null)) {
            byte[] data = newData();
            ChannelFuture writeFuture = peers.client.writeAndFlush(Unpooled.wrappedBuffer(data));
            // The server starts a renegotiation. The client processes the HelloRequest in a delegated task, which
            // either still runs, or completed already and the client waits for the ServerHello.
            peers.serverSslHandler.renegotiate();
            peers.transferToClient();
            assertThat(peers.executor != null && peers.executor.hasTasks()).isEqualTo(taskRunning);

            // All the data must be wrapped before the outbound side of the engine is closed, as it would be failed
            // otherwise. We can not wrap while a task runs, so this needs to wait for it.
            ChannelFuture closeFuture = closeChannel ? peers.client.close() : peers.clientSslHandler.closeOutbound();
            assertThat(writeFuture.isDone()).isFalse();
            peers.runTasks();
            assertThat(peers.holder.appDataBytes).isGreaterThan(DATA_LENGTH);
            peers.holder.release();
            assertThat(writeFuture.isSuccess()).as(String.valueOf(writeFuture.cause())).isTrue();
            assertThat(closeFuture.isSuccess()).as(String.valueOf(closeFuture.cause())).isTrue();

            try {
                peers.transferToServer();
            } catch (DecoderException e) {
                // The JDK reports the close_notify that the server receives in the middle of the renegotiation it
                // started as an error. This happens after it received all the data.
                assertThat(e).hasMessageContaining("close_notify during handshake");
            }
            peers.assertServerReceived(data);
        }
    }

    @Test
    @Timeout(30)
    public void testCloseOutboundAndCloseWhileDelegatedTaskRuns() throws Exception {
        try (Peers peers = new Peers(SslProvider.JDK, SslProtocols.TLS_v1_2, new ManualExecutor())) {
            byte[] data = newData();
            ChannelFuture writeFuture = peers.client.writeAndFlush(Unpooled.wrappedBuffer(data));
            peers.serverSslHandler.renegotiate();
            peers.transferToClient();
            assertThat(peers.executor.hasTasks()).isTrue();

            // Both have to wait for the task, and are notified once the close_notify was written.
            ChannelFuture closeOutboundFuture = peers.clientSslHandler.closeOutbound();
            ChannelFuture closeFuture = peers.client.close();
            peers.runTasks();
            assertThat(closeOutboundFuture.isDone()).isFalse();
            assertThat(closeFuture.isDone()).isFalse();
            peers.holder.release();
            assertThat(writeFuture.isSuccess()).as(String.valueOf(writeFuture.cause())).isTrue();
            assertThat(closeOutboundFuture.isSuccess()).as(String.valueOf(closeOutboundFuture.cause())).isTrue();
            assertThat(closeFuture.isSuccess()).as(String.valueOf(closeFuture.cause())).isTrue();

            try {
                peers.transferToServer();
            } catch (DecoderException e) {
                assertThat(e).hasMessageContaining("close_notify during handshake");
            }
            peers.assertServerReceived(data);
        }
    }

    @Test
    @Timeout(30)
    public void testWrapIsSuspendedWhenSslHandlerUsesOtherExecutors() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        EventExecutorGroup sslHandlerExecutor = new DefaultEventExecutorGroup(1);
        ExecutorService delegatedTaskExecutor = Executors.newSingleThreadExecutor();
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        final SslContext serverContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .sslProvider(SslProvider.JDK).build();
        final SslContext clientContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).sslProvider(SslProvider.JDK).build();
        final byte[] data = newData();
        final ByteBuf received = Unpooled.buffer(data.length);
        final Promise<Void> receivedAll = group.next().newPromise();
        final MaxPendingWritesHandler maxPendingWrites = new MaxPendingWritesHandler();
        Channel server = null;
        Channel client = null;
        try {
            server = new ServerBootstrap().group(group).channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(serverContext.newHandler(ch.alloc()));
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    received.writeBytes(buf);
                                    buf.release();
                                    if (received.readableBytes() == data.length) {
                                        receivedAll.trySuccess(null);
                                    }
                                }
                            });
                        }
                    }).bind(LocalAddress.ANY).sync().channel();
            client = new Bootstrap().group(group).channel(LocalChannel.class)
                    .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(HIGH_WATER_MARK / 2, HIGH_WATER_MARK))
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(maxPendingWrites);
                            ch.pipeline().addLast(sslHandlerExecutor,
                                    clientContext.newHandler(ch.alloc(), delegatedTaskExecutor));
                        }
                    }).connect(server.localAddress()).sync().channel();
            client.pipeline().get(SslHandler.class).handshakeFuture().sync();

            client.writeAndFlush(Unpooled.wrappedBuffer(data)).sync();
            receivedAll.sync();
            assertThat(maxPendingWrites.maxPendingBytes.get()).isLessThanOrEqualTo(MAX_HELD_BYTES);
            assertThat(ByteBufUtil.getBytes(received)).isEqualTo(data);
        } finally {
            if (client != null) {
                client.close().sync();
            }
            if (server != null) {
                server.close().sync();
            }
            group.shutdownGracefully();
            sslHandlerExecutor.shutdownGracefully();
            delegatedTaskExecutor.shutdown();
            received.release();
            ReferenceCountUtil.release(clientContext);
            ReferenceCountUtil.release(serverContext);
        }
    }

    @ParameterizedTest
    @EnumSource(SslProvider.class)
    @Timeout(30)
    public void testStartTlsDoesNotWriteDataUnencryptedWhenPeerStartsHandshakeEarly(SslProvider provider)
            throws Exception {
        assumeTrue(provider == SslProvider.JDK || OpenSsl.isAvailable(), "OpenSSL is not available");
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContext serverContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .sslProvider(provider).build();
        SslContext clientContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).sslProvider(SslProvider.JDK).build();
        SslHandler clientSslHandler = clientContext.newHandler(UnpooledByteBufAllocator.DEFAULT);
        EmbeddedChannel server = new EmbeddedChannel(
                new SslHandler(serverContext.newEngine(UnpooledByteBufAllocator.DEFAULT), true));
        server.config().setWriteBufferWaterMark(new WriteBufferWaterMark(HIGH_WATER_MARK / 2, HIGH_WATER_MARK));
        EmbeddedChannel client = new EmbeddedChannel(clientSslHandler);
        try {
            byte[] data = newData();
            // The first message that is flushed is written unencrypted with startTls.
            ChannelFuture writeFuture = server.write(Unpooled.wrappedBuffer(data));
            // The client starts the handshake before the server flushed though, so the server wraps the data once the
            // handshake completed. All of it needs to be wrapped, as it would be written unencrypted otherwise.
            for (int i = 0; i < 100 && !(clientSslHandler.handshakeFuture().isDone() && writeFuture.isDone()); i++) {
                transfer(client, server);
                transfer(server, client);
            }
            server.flush();
            transfer(server, client);
            assertThat(writeFuture.isSuccess()).as(String.valueOf(writeFuture.cause())).isTrue();

            ByteBuf received = Unpooled.buffer(data.length);
            try {
                ByteBuf in;
                while ((in = client.readInbound()) != null) {
                    received.writeBytes(in);
                    in.release();
                }
                assertThat(ByteBufUtil.getBytes(received)).isEqualTo(data);
            } finally {
                received.release();
            }
        } finally {
            client.finishAndReleaseAll();
            server.finishAndReleaseAll();
            ReferenceCountUtil.release(clientContext);
            ReferenceCountUtil.release(serverContext);
        }
    }

    private static void transfer(EmbeddedChannel from, EmbeddedChannel to) {
        ByteBuf buf;
        while ((buf = from.readOutbound()) != null) {
            to.writeInbound(buf);
        }
    }

    /**
     * A client and a server that completed the TLS handshake. All flushes of the client are held back by
     * {@link #holder}, like a transport that can not keep up would do.
     */
    private static final class Peers implements AutoCloseable {
        final SslContext clientContext;
        final SslContext serverContext;
        final ManualExecutor executor;
        final SslHandler clientSslHandler;
        final SslHandler serverSslHandler;
        final EmbeddedChannel client;
        final EmbeddedChannel server;
        final FlushHoldingHandler holder = new FlushHoldingHandler();

        Peers(SslProvider provider) throws Exception {
            this(provider, null, null);
        }

        /**
         * @param protocol the TLS protocol to use, or {@code null} for the default ones.
         * @param executor runs the delegated tasks of the client, or {@code null} to run them directly.
         */
        Peers(SslProvider provider, String protocol, ManualExecutor executor) throws Exception {
            assumeTrue(provider == SslProvider.JDK || OpenSsl.isAvailable(), "OpenSSL is not available");
            SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
            SslContextBuilder serverBuilder = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                    .sslProvider(SslProvider.JDK);
            SslContextBuilder clientBuilder = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).sslProvider(provider);
            if (protocol != null) {
                serverBuilder.protocols(protocol);
                clientBuilder.protocols(protocol);
            }
            serverContext = serverBuilder.build();
            clientContext = clientBuilder.build();
            this.executor = executor;
            clientSslHandler = executor == null ? clientContext.newHandler(UnpooledByteBufAllocator.DEFAULT) :
                    clientContext.newHandler(UnpooledByteBufAllocator.DEFAULT, executor);
            serverSslHandler = serverContext.newHandler(UnpooledByteBufAllocator.DEFAULT);
            client = new EmbeddedChannel(clientSslHandler);
            server = new EmbeddedChannel(serverSslHandler);
            client.config().setWriteBufferWaterMark(new WriteBufferWaterMark(HIGH_WATER_MARK / 2, HIGH_WATER_MARK));

            for (int i = 0; i < 100 && !(clientSslHandler.handshakeFuture().isDone() &&
                    serverSslHandler.handshakeFuture().isDone()); i++) {
                transfer(client, server);
                transfer(server, client);
                runTasks();
            }
            assertThat(clientSslHandler.handshakeFuture().isSuccess()).isTrue();
            assertThat(serverSslHandler.handshakeFuture().isSuccess()).isTrue();
            client.pipeline().addFirst(holder);
        }

        void transferToServer() {
            transfer(client, server);
        }

        void transferToClient() {
            transfer(server, client);
        }

        /**
         * Runs the delegated tasks of the client, and what the client does on its event loop once they completed.
         */
        void runTasks() {
            if (executor != null) {
                while (executor.runTasks()) {
                    client.runPendingTasks();
                }
            }
        }

        /**
         * Lets the client and the server exchange everything until {@code future} is done.
         */
        void exchangeUntilDone(ChannelFuture future) {
            for (int i = 0; i < 1000 && !future.isDone(); i++) {
                holder.release();
                transferToServer();
                transferToClient();
                runTasks();
            }
        }

        void assertServerReceived(byte[] data) {
            ByteBuf received = Unpooled.buffer(data.length);
            try {
                ByteBuf in;
                while ((in = server.readInbound()) != null) {
                    received.writeBytes(in);
                    in.release();
                }
                assertThat(ByteBufUtil.getBytes(received)).isEqualTo(data);
            } finally {
                received.release();
            }
        }

        @Override
        public void close() {
            try {
                if (client.pipeline().context(holder) != null) {
                    client.pipeline().remove(holder);
                }
                client.finishAndReleaseAll();
                server.finishAndReleaseAll();
            } finally {
                ReferenceCountUtil.release(clientContext);
                ReferenceCountUtil.release(serverContext);
            }
        }
    }

    /**
     * Holds back all flushes until {@link #release()} is called, like a transport that can not write anything until
     * then. The writes stay in the {@link io.netty.channel.ChannelOutboundBuffer}, so their promises are not complete
     * and they count towards the writability of the channel.
     */
    private static final class FlushHoldingHandler extends ChannelOutboundHandlerAdapter {
        private ChannelHandlerContext ctx;
        int heldBytes;
        /** The bytes of the held back application data records, without handshake and alert records. */
        int appDataBytes;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ByteBuf buf = (ByteBuf) msg;
            heldBytes += buf.readableBytes();
            for (int i = buf.readerIndex(); i + 5 <= buf.writerIndex(); i += 5 + buf.getUnsignedShort(i + 3)) {
                if (buf.getUnsignedByte(i) == APPLICATION_DATA) {
                    appDataBytes += 5 + buf.getUnsignedShort(i + 3);
                }
            }
            ctx.write(msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            // Held back until release() is called.
        }

        /**
         * Flushes all held back writes, which completes their promises.
         */
        void release() {
            heldBytes = 0;
            appDataBytes = 0;
            ctx.flush();
        }
    }

    /**
     * Holds back all writes until they are released one by one, like a transport that can only write some of them.
     */
    private static final class WriteHoldingHandler extends ChannelOutboundHandlerAdapter {
        private final Queue<ByteBuf> bufs = new ArrayDeque<ByteBuf>();
        private final Queue<ChannelPromise> promises = new ArrayDeque<ChannelPromise>();
        /** The number of bytes that were held back when each of the writes arrived. */
        final List<Integer> heldBytesOnWrite = new ArrayList<Integer>();
        private ChannelHandlerContext ctx;
        int heldBytes;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            ByteBuf buf;
            while ((buf = bufs.poll()) != null) {
                buf.release();
                promises.remove().setFailure(new ClosedChannelException());
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ByteBuf buf = (ByteBuf) msg;
            heldBytesOnWrite.add(heldBytes);
            heldBytes += buf.readableBytes();
            bufs.add(buf);
            promises.add(promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            // Held back until the writes are released.
        }

        /**
         * Writes and flushes the oldest held back write, which completes its promise.
         */
        void releaseOldest() {
            ByteBuf buf = bufs.remove();
            heldBytes -= buf.readableBytes();
            ctx.writeAndFlush(buf, promises.remove());
        }
    }

    /**
     * Records the maximum number of bytes that were written but not written by the transport yet. It runs on the
     * event loop of the channel, which completes the writes.
     */
    private static final class MaxPendingWritesHandler extends ChannelOutboundHandlerAdapter {
        private long pendingBytes;
        final AtomicLong maxPendingBytes = new AtomicLong();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, final ChannelPromise promise) {
            final int bytes = ((ByteBuf) msg).readableBytes();
            pendingBytes += bytes;
            if (pendingBytes > maxPendingBytes.get()) {
                maxPendingBytes.set(pendingBytes);
            }
            // Use our own promise, as the listeners of the promise we got are notified on the executor of the
            // handler that created it.
            ctx.write(msg, ctx.newPromise().addListener(f -> {
                pendingBytes -= bytes;
                if (f.isSuccess()) {
                    promise.setSuccess();
                } else {
                    promise.setFailure(f.cause());
                }
            }));
        }
    }

    /**
     * Runs the delegated tasks of a {@link SslHandler} only when asked to.
     */
    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<Runnable>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        boolean hasTasks() {
            return !tasks.isEmpty();
        }

        /**
         * Runs the queued tasks and returns {@code true} if there were any.
         */
        boolean runTasks() {
            boolean ran = false;
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
                ran = true;
            }
            return ran;
        }
    }
}

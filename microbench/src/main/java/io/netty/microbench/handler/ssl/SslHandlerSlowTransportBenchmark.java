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
package io.netty.microbench.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.microbench.channel.EmbeddedChannelHandlerContext;
import io.netty.microbench.channel.EmbeddedChannelWriteAccumulatingHandlerContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.ByteToMessageDecoder.COMPOSITE_CUMULATOR;

/**
 * Writes and flushes {@link #writeSize} bytes at once through a {@link SslHandler} to a transport that can only write
 * {@link #transportWriteSize} bytes each time it is writable, like a socket whose send buffer is full. Besides the
 * time this takes, {@link PendingEncryptedBytesProfiler} reports the most encrypted data the {@link SslHandler}
 * produced ahead of what the transport could write, which is the memory it needed for it.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class SslHandlerSlowTransportBenchmark extends AbstractSslHandlerBenchmark {

    /**
     * The most encrypted data that was waiting to be written by the transport at the same time in this iteration.
     */
    private static volatile long maxPendingEncryptedBytes;

    @Param({ "16384", "1048576" })
    public int writeSize;

    /**
     * How many bytes the transport can write each time it is writable. {@link Integer#MAX_VALUE} is a transport that
     * can write everything that was written to it so far.
     */
    @Param({ "65536", "2147483647" })
    public int transportWriteSize;

    private ByteBufAllocator allocator;
    private ByteBuf data;
    private SlowTransportHandlerContext clientTransport;
    private EmbeddedChannelWriteAccumulatingHandlerContext serverTransport;

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(PendingEncryptedBytesProfiler.class);
    }

    @Setup(Level.Iteration)
    public void setup() throws Exception {
        allocator = new PooledByteBufAllocator(true);
        clientSslHandler = newClientHandler(allocator);
        serverSslHandler = newServerHandler(allocator);
        clientTransport = new SlowTransportHandlerContext(allocator, clientSslHandler);
        serverTransport = new EmbeddedChannelWriteAccumulatingHandlerContext(
                allocator, serverSslHandler, COMPOSITE_CUMULATOR) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };

        byte[] bytes = new byte[writeSize];
        ThreadLocalRandom.current().nextBytes(bytes);
        data = allocator.directBuffer(writeSize).writeBytes(bytes);

        // Complete the TLS handshake.
        serverSslHandler.handlerAdded(serverTransport);
        clientSslHandler.handlerAdded(clientTransport);
        do {
            ByteBuf fromClient = clientTransport.writeAllToSocket();
            if (fromClient != null) {
                serverSslHandler.channelRead(serverTransport, fromClient);
            }
            ByteBuf fromServer = serverTransport.cumulation();
            if (fromServer != null) {
                clientSslHandler.channelRead(clientTransport, fromServer.retain());
                serverTransport.releaseCumulation();
            }
        } while (!clientSslHandler.handshakeFuture().isDone() || !serverSslHandler.handshakeFuture().isDone());
        handleUnexpectedException(clientSslHandler.handshakeFuture().cause());
        handleUnexpectedException(serverSslHandler.handshakeFuture().cause());
        clientTransport.maxPendingBytes = 0;
    }

    @TearDown(Level.Iteration)
    public void tearDown() throws Exception {
        destroySslHandlers();
        data.release();
        serverTransport.releaseCumulation();
    }

    @Benchmark
    public void writeToSlowTransport() throws Exception {
        long writtenBytes = clientTransport.writtenBytes;
        clientSslHandler.write(clientTransport, data.retainedSlice(), clientTransport.voidPromise());
        clientSslHandler.flush(clientTransport);
        while (clientTransport.hasPendingWrites()) {
            clientTransport.writeToSocket(transportWriteSize);
        }
        // Make sure that the SslHandler did not stop writing, as this would make the results look better.
        if (clientTransport.writtenBytes - writtenBytes < writeSize) {
            throw new IllegalStateException("Only " + (clientTransport.writtenBytes - writtenBytes) + " of " +
                    writeSize + " bytes were written");
        }
        if (clientTransport.maxPendingBytes > maxPendingEncryptedBytes) {
            maxPendingEncryptedBytes = clientTransport.maxPendingBytes;
        }
    }

    /**
     * Reports the most encrypted data that was waiting to be written by the transport at the same time. It is added
     * when running the benchmark through {@link #run()}, and can be added with {@code -prof} otherwise. We do not use
     * {@link org.openjdk.jmh.annotations.AuxCounters} for this, as JMH sums those up for all iterations.
     */
    public static final class PendingEncryptedBytesProfiler implements InternalProfiler {
        @Override
        public String getDescription() {
            return "The most encrypted data that was waiting to be written by the transport at the same time";
        }

        @Override
        public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
            maxPendingEncryptedBytes = 0;
        }

        @Override
        public Collection<? extends Result> afterIteration(
                BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
            return Collections.singletonList(new ScalarResult(
                    "maxPendingEncryptedBytes", maxPendingEncryptedBytes, "bytes", AggregationPolicy.MAX));
        }
    }

    /**
     * Holds back the writes until {@link #writeToSocket(int)} is called, like a transport whose send buffer is full.
     */
    private static final class SlowTransportHandlerContext extends EmbeddedChannelHandlerContext {
        private final ArrayDeque<ByteBuf> bufs = new ArrayDeque<ByteBuf>();
        private final ArrayDeque<ChannelPromise> promises = new ArrayDeque<ChannelPromise>();
        private final byte[] socketBuffer = new byte[64 * 1024];
        long pendingBytes;
        long writtenBytes;
        long maxPendingBytes;

        SlowTransportHandlerContext(ByteBufAllocator alloc, ChannelHandler handler) {
            super(alloc, handler, new EmbeddedChannel());
        }

        @Override
        protected void handleException(Throwable t) {
            handleUnexpectedException(t);
        }

        @Override
        public ChannelFuture write(Object msg) {
            return write(msg, newPromise());
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            ByteBuf buf = (ByteBuf) msg;
            pendingBytes += buf.readableBytes();
            maxPendingBytes = Math.max(maxPendingBytes, pendingBytes);
            bufs.add(buf);
            promises.add(promise);
            return promise;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return write(msg, promise);
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return write(msg);
        }

        boolean hasPendingWrites() {
            return !bufs.isEmpty();
        }

        /**
         * Writes up to {@code bytes} of the pending data, and completes the writes that were written completely. The
         * data is copied, like writing it to a socket does.
         */
        void writeToSocket(int bytes) {
            ByteBuf buf;
            while (bytes > 0 && (buf = bufs.peek()) != null) {
                int length = Math.min(bytes, buf.readableBytes());
                bytes -= length;
                pendingBytes -= length;
                writtenBytes += length;
                while (length > 0) {
                    int n = Math.min(length, socketBuffer.length);
                    buf.readBytes(socketBuffer, 0, n);
                    length -= n;
                }
                if (!buf.isReadable()) {
                    bufs.remove();
                    buf.release();
                    // This may let the SslHandler write more.
                    promises.remove().setSuccess();
                }
            }
        }

        /**
         * Writes all the pending data and returns it, or {@code null} if there is none.
         */
        ByteBuf writeAllToSocket() {
            if (bufs.isEmpty()) {
                return null;
            }
            ByteBuf all = alloc().buffer();
            ByteBuf buf;
            while ((buf = bufs.poll()) != null) {
                pendingBytes -= buf.readableBytes();
                all.writeBytes(buf);
                buf.release();
                promises.remove().setSuccess();
            }
            return all;
        }
    }
}

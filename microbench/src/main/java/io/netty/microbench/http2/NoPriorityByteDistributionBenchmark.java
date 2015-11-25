/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.http2;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.Http2StreamVisitor;
import io.netty.handler.codec.http2.PriorityStreamByteDistributor;
import io.netty.handler.codec.http2.StreamByteDistributor;
import io.netty.handler.codec.http2.UniformStreamByteDistributor;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.net.SocketAddress;

/**
 * Benchmark to compare stream byte distribution algorithms when priorities are identical for
 * all streams.
 */
@Fork(1)
@State(Scope.Benchmark)
public class NoPriorityByteDistributionBenchmark extends AbstractMicrobenchmark {
    public enum Algorithm {
        PRIORITY,
        UNIFORM
    }

    @Param({ "100", "10000" })
    private int numStreams;

    @Param({ "1024", "65536", "1048576" })
    private int windowSize;

    @Param
    private Algorithm algorithm;

    private Http2Connection connection;

    private Http2Connection.PropertyKey dataRefresherKey;

    private Http2RemoteFlowController controller;

    private StreamByteDistributor distributor;

    private AdditionalCounters counters;

    /**
     * Additional counters for a single iteration.
     */
    @AuxCounters
    @State(Scope.Thread)
    public static class AdditionalCounters {
        private int minWriteSize;
        private int maxWriteSize;
        private long totalBytes;
        private long numWrites;
        private int invocations;

        public int minWriteSize() {
            return minWriteSize;
        }

        public int avgWriteSize() {
            return (int) (totalBytes / numWrites);
        }

        public int maxWriteSize() {
            return maxWriteSize;
        }
    }

    private Http2StreamVisitor invocationVisitor = new Http2StreamVisitor() {
        @Override
        public boolean visit(Http2Stream stream) throws Http2Exception {
            // Restore the connection window.
            resetWindow(stream);

            // Restore the data to each stream.
            dataRefresher(stream).refreshData();
            return true;
        }
    };

    @Setup(Level.Trial)
    public void setupTrial() throws Http2Exception {
        connection = new DefaultHttp2Connection(false);
        dataRefresherKey = connection.newKey();

        // Create the flow controller
        switch (algorithm) {
            case PRIORITY:
                distributor = new PriorityStreamByteDistributor(connection);
                break;
            case UNIFORM:
                distributor = new UniformStreamByteDistributor(connection);
                break;
        }
        controller = new DefaultHttp2RemoteFlowController(connection, new ByteCounter(distributor));
        controller.channelHandlerContext(new TestContext());

        // Create the streams, each initialized with MAX_INT bytes.
        for (int i = 0; i < numStreams; ++i) {
            Http2Stream stream = connection.local().createStream(toStreamId(i), false);
            addData(stream, Integer.MAX_VALUE);
            stream.setProperty(dataRefresherKey, new DataRefresher(stream));
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() throws Http2Exception {
        resetWindow(connection.connectionStream());
        connection.forEachActiveStream(invocationVisitor);
    }

    @Benchmark
    public void write(AdditionalCounters counters) throws Http2Exception {
        // Set up for this invocation. Doing this in the benchmark method since this
        // seems to throw off the counters when run as a setup step for the invocation.
        this.counters = counters;
        counters.invocations++;

        // Now run the benchmark method.
        controller.writePendingBytes();
    }

    private void resetWindow(Http2Stream stream) throws Http2Exception {
        controller.incrementWindowSize(stream, windowSize - controller.windowSize(stream));
    }

    private DataRefresher dataRefresher(Http2Stream stream) {
        return (DataRefresher) stream.getProperty(dataRefresherKey);
    }

    private void addData(Http2Stream stream, final int dataSize) {
        controller.addFlowControlled(stream, new Http2RemoteFlowController.FlowControlled() {
            int size = dataSize;

            @Override
            public int size() {
                return size;
            }

            @Override
            public void error(ChannelHandlerContext ctx, Throwable cause) {
                cause.printStackTrace();
            }

            @Override
            public void writeComplete() {
                // Don't care.
            }

            @Override
            public void write(ChannelHandlerContext ctx, int allowedBytes) {
                size -= allowedBytes;
            }

            @Override
            public boolean merge(ChannelHandlerContext ctx,
                                 Http2RemoteFlowController.FlowControlled next) {
                int nextSize = next.size();
                if (Integer.MAX_VALUE - nextSize < size) {
                    // Disallow merge to avoid integer overflow.
                    return false;
                }

                // Merge.
                size += nextSize;
                return true;
            }
        });
    }

    private static int toStreamId(int i) {
        return 2 * i + 1;
    }

    private final class DataRefresher {
        private final Http2Stream stream;
        private int data;

        private DataRefresher(Http2Stream stream) {
            this.stream = stream;
        }

        void add(int data) {
            this.data += data;
        }

        void refreshData() {
            if (data > 0) {
                addData(stream, data);
                data = 0;
            }
        }
    }

    private final class ByteCounter implements StreamByteDistributor {
        private final StreamByteDistributor delegate;

        private ByteCounter(StreamByteDistributor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void updateStreamableBytes(StreamState state) {
            delegate.updateStreamableBytes(state);
        }

        @Override
        public boolean distribute(int maxBytes, Writer writer)
                throws Http2Exception {
            return delegate.distribute(maxBytes, new CountingWriter(writer));
        }

        private final class CountingWriter implements Writer {
            private final Writer delegate;

            private CountingWriter(Writer delegate) {
                this.delegate = delegate;
            }

            @Override
            public void write(Http2Stream stream, int numBytes) {
                if (numBytes > 0) {
                    // Add the data to the refresher so that it can be given back to the
                    // stream at the end of the iteration.
                    DataRefresher refresher = dataRefresher(stream);
                    refresher.add(numBytes);

                    counters.numWrites++;
                    counters.totalBytes += numBytes;
                    if (counters.minWriteSize == 0 || numBytes < counters.minWriteSize) {
                        counters.minWriteSize = numBytes;
                    }
                    if (numBytes > counters.maxWriteSize) {
                        counters.maxWriteSize = numBytes;
                    }
                }

                delegate.write(stream, numBytes);
            }
        }
    }

    private static class TestContext implements ChannelHandlerContext {

        private Channel channel = new TestChannel();

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public EventExecutor executor() {
            return channel.eventLoop();
        }

        @Override
        public ChannelHandlerInvoker invoker() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandler handler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isRemoved() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture disconnect() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture deregister() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress,
                                     ChannelPromise promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelHandlerContext read() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture write(Object msg) {
            return channel.newSucceededFuture();
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return promise;
        }

        @Override
        public ChannelHandlerContext flush() {
            return this;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return promise;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return null;
        }

        @Override
        public ChannelPipeline pipeline() {
            return channel.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return channel.alloc();
        }

        @Override
        public ChannelPromise newPromise() {
            return channel.newPromise();
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return channel.newProgressivePromise();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return channel.newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return channel.newFailedFuture(cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            return channel.voidPromise();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return channel.attr(key);
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return channel.hasAttr(key);
        }
    }

    private static class TestChannel extends AbstractChannel {
        private static final ChannelMetadata TEST_METADATA = new ChannelMetadata(false);
        private DefaultChannelConfig config = new DefaultChannelConfig(this);

        private class TestUnsafe extends AbstractUnsafe {
            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            }
        }

        public TestChannel() {
            super(null);
            config.setWriteBufferHighWaterMark(Integer.MAX_VALUE);
            config.setWriteBufferLowWaterMark(Integer.MAX_VALUE);
        }

        @Override
        public long bytesBeforeUnwritable() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isWritable() {
            return true;
        }

        @Override
        public ChannelConfig config() {
            return new DefaultChannelConfig(this);
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public ChannelMetadata metadata() {
            return TEST_METADATA;
        }

        @Override
        protected AbstractUnsafe newUnsafe() {
            return new TestUnsafe();
        }

        @Override
        protected boolean isCompatible(EventLoop loop) {
            return true;
        }

        @Override
        protected SocketAddress localAddress0() {
            return null;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return null;
        }

        @Override
        protected void doBind(SocketAddress localAddress) throws Exception { }

        @Override
        protected void doDisconnect() throws Exception { }

        @Override
        protected void doClose() throws Exception { }

        @Override
        protected void doBeginRead() throws Exception { }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) throws Exception { }
    }
}

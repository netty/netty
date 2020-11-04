/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.microbench.http2;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.Http2StreamVisitor;
import io.netty.handler.codec.http2.StreamByteDistributor;
import io.netty.handler.codec.http2.UniformStreamByteDistributor;
import io.netty.handler.codec.http2.WeightedFairQueueByteDistributor;
import io.netty.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

/**
 * Benchmark to compare stream byte distribution algorithms when priorities are identical for
 * all streams.
 */
@Threads(1)
@State(Scope.Benchmark)
public class NoPriorityByteDistributionBenchmark extends AbstractMicrobenchmark {
    public enum Algorithm {
        WFQ,
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
    private ChannelHandlerContext ctx;

    public NoPriorityByteDistributionBenchmark() {
        super(true);
    }

    /**
     * Additional counters for a single iteration.
     */
    @AuxCounters
    @State(Scope.Thread)
    public static class AdditionalCounters {
        int minWriteSize = Integer.MAX_VALUE;
        int maxWriteSize = Integer.MIN_VALUE;
        long totalBytes;
        long numWrites;
        int invocations;

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

    @TearDown(Level.Trial)
    public void tearDownTrial() throws Exception {
        ctx.close();
    }

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        connection = new DefaultHttp2Connection(false);
        dataRefresherKey = connection.newKey();

        // Create the flow controller
        switch (algorithm) {
            case WFQ:
                distributor = new WeightedFairQueueByteDistributor(connection, 0);
                break;
            case UNIFORM:
                distributor = new UniformStreamByteDistributor(connection);
                break;
        }
        controller = new DefaultHttp2RemoteFlowController(connection, new ByteCounter(distributor));
        connection.remote().flowController(controller);
        Http2ConnectionHandler handler = new Http2ConnectionHandlerBuilder()
            .encoderEnforceMaxConcurrentStreams(false).validateHeaders(false)
            .frameListener(new Http2FrameAdapter())
            .connection(connection)
            .build();
        ctx = new EmbeddedChannelWriteReleaseHandlerContext(PooledByteBufAllocator.DEFAULT, handler) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
        handler.handlerAdded(ctx);
        handler.channelActive(ctx);

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
            private int size = dataSize;

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
        public void updateDependencyTree(int childStreamId, int parentStreamId, short weight, boolean exclusive) {
            delegate.updateDependencyTree(childStreamId, parentStreamId, weight, exclusive);
        }

        @Override
        public boolean distribute(int maxBytes, Writer writer) throws Http2Exception {
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

                    ++counters.numWrites;
                    counters.totalBytes += numBytes;
                    if (numBytes < counters.minWriteSize) {
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
}

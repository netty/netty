/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.http2;

import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.microbench.util.AbstractMicrobenchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

/**
 * This benchmark is designed to exercise re-prioritization events and measure the ops/sec.
 */
@Threads(1)
@State(Scope.Benchmark)
public class Http2PriorityTreeBenchmark extends AbstractMicrobenchmark {
    private static final short WEIGHT = 1;

    public Http2Connection connection;

    @Param({ "1000", "10000" })
    public int numStreams;

    @Param({ "10", "100", "1000" })
    public int d_ary;

    @Param({ "10", "100", "1000" })
    public int exclusiveOnCount;

    @Setup(Level.Trial)
    public void setup() throws Http2Exception {
        connection = new DefaultHttp2Connection(false);
        for (int i = 0; i < numStreams; ++i) {
            connection.local().createStream(toStreamId(i), false);
        }
    }

    @TearDown(Level.Iteration)
    public void teardown() throws Http2Exception {
        final int connectionId = connection.connectionStream().id();
        for (int i = 0; i < numStreams; ++i) {
            connection.stream(toStreamId(i)).setPriority(connectionId, WEIGHT, false);
        }
    }

    /**
     * A priority tree will be build using the {@link #d_ary} variable to determine the number of children for each
     * node. After the priority tree is built the nodes closest to the root will be pushed down to be dependent on leaf
     * nodes. This is to simulate an "expensive" tree operation.
     */
    @Benchmark
    public void prioritizeStreams() throws Http2Exception {
        int streamId = 0;
        int parentId = 0;
        boolean exclusive = false;
        for (int i = 0; i < numStreams; ++i) {
            // Treat all streams as they exist in a logical array in the range [0, numStreams].
            // From this we can find the current parent stream via a i / d_ary operation.
            parentId = toStreamId(i / d_ary);
            streamId = toStreamId(i);
            if (parentId == streamId) {
                exclusive = i % exclusiveOnCount == 0;
                continue;
            }
            Http2Stream stream = connection.stream(streamId);
            stream.setPriority(parentId, WEIGHT, exclusive);
            exclusive = i % exclusiveOnCount == 0;
        }

        // Now change the parent assignments by pushing the root nodes out to the leafs.
        for (int i = 0; i < numStreams; ++i) {
            parentId = toStreamId((numStreams - i) / d_ary);
            streamId = toStreamId(i);
            if (parentId == streamId) {
                exclusive = i % exclusiveOnCount == 0;
                continue;
            }
            Http2Stream stream = connection.stream(streamId);
            stream.setPriority(parentId, WEIGHT, exclusive);
            exclusive = i % exclusiveOnCount == 0;
        }
    }

    private static int toStreamId(int i) {
        return 2 * i + 1;
    }
}

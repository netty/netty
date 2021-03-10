/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.handler.codec.http2.Http2TestUtil.TestStreamByteDistributorStreamState;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

abstract class AbstractWeightedFairQueueByteDistributorDependencyTest {
    Http2Connection connection;
    WeightedFairQueueByteDistributor distributor;
    private IntObjectMap<TestStreamByteDistributorStreamState> stateMap =
            new IntObjectHashMap<TestStreamByteDistributorStreamState>();

    @Mock
    StreamByteDistributor.Writer writer;

    Http2Stream stream(int streamId) {
        return connection.stream(streamId);
    }

    Answer<Void> writeAnswer(final boolean closeIfNoFrame) {
        return new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock in) throws Throwable {
                Http2Stream stream = in.getArgument(0);
                int numBytes = in.getArgument(1);
                TestStreamByteDistributorStreamState state = stateMap.get(stream.id());
                state.pendingBytes -= numBytes;
                state.hasFrame = state.pendingBytes > 0;
                state.isWriteAllowed = state.hasFrame;
                if (closeIfNoFrame && !state.hasFrame) {
                    stream.close();
                }
                distributor.updateStreamableBytes(state);
                return null;
            }
        };
    }

    void initState(final int streamId, final long streamableBytes, final boolean hasFrame) {
        initState(streamId, streamableBytes, hasFrame, hasFrame);
    }

    void initState(final int streamId, final long pendingBytes, final boolean hasFrame,
                              final boolean isWriteAllowed) {
        final Http2Stream stream = stream(streamId);
        TestStreamByteDistributorStreamState state = new TestStreamByteDistributorStreamState(stream, pendingBytes,
                hasFrame, isWriteAllowed);
        stateMap.put(streamId, state);
        distributor.updateStreamableBytes(state);
    }

    void setPriority(int streamId, int parent, int weight, boolean exclusive) throws Http2Exception {
        distributor.updateDependencyTree(streamId, parent, (short) weight, exclusive);
    }
}

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

import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

abstract class AbstractWeightedFairQueueByteDistributorDependencyTest {
    Http2Connection connection;
    WeightedFairQueueByteDistributor distributor;

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
                int streamableBytes = distributor.streamableBytes0(stream) - numBytes;
                boolean hasFrame = streamableBytes > 0;
                updateStream(stream.id(), streamableBytes, hasFrame, hasFrame, closeIfNoFrame);
                return null;
            }
        };
    }

    void updateStream(final int streamId, final int streamableBytes, final boolean hasFrame) {
        updateStream(streamId, streamableBytes, hasFrame, hasFrame, false);
    }

    void updateStream(final int streamId, final int pendingBytes, final boolean hasFrame,
                              final boolean isWriteAllowed, boolean closeIfNoFrame) {
        final Http2Stream stream = stream(streamId);
        if (closeIfNoFrame && !hasFrame) {
            stream(streamId).close();
        }
        distributor.updateStreamableBytes(new StreamByteDistributor.StreamState() {
            @Override
            public Http2Stream stream() {
                return stream;
            }

            @Override
            public int pendingBytes() {
                return pendingBytes;
            }

            @Override
            public boolean hasFrame() {
                return hasFrame;
            }

            @Override
            public int windowSize() {
                return isWriteAllowed ? pendingBytes : -1;
            }
        });
    }

    void setPriority(int streamId, int parent, int weight, boolean exclusive) throws Http2Exception {
        distributor.updateDependencyTree(streamId, parent, (short) weight, exclusive);
    }
}

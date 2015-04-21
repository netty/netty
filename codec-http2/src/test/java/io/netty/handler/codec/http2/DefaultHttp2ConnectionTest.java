/*
 * Copyright 2014 The Netty Project
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

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_WEIGHT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.Http2Connection.Endpoint;
import io.netty.handler.codec.http2.Http2Stream.State;
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DefaultHttp2Connection}.
 */
public class DefaultHttp2ConnectionTest {

    private DefaultHttp2Connection server;
    private DefaultHttp2Connection client;

    @Mock
    private Http2Connection.Listener clientListener;

    @Mock
    private Http2Connection.Listener clientListener2;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        server = new DefaultHttp2Connection(true);
        client = new DefaultHttp2Connection(false);
        client.addListener(clientListener);
    }

    @Test
    public void getStreamWithoutStreamShouldReturnNull() {
        assertNull(server.stream(100));
    }

    @Test
    public void goAwayReceivedShouldCloseStreamsGreaterThanLastStream() throws Exception {
        Http2Stream stream1 = client.local().createStream(3).open(false);
        Http2Stream stream2 = client.local().createStream(5).open(false);
        Http2Stream remoteStream = client.remote().createStream(4).open(false);

        assertEquals(State.OPEN, stream1.state());
        assertEquals(State.OPEN, stream2.state());

        client.goAwayReceived(3, 8, null);

        assertEquals(State.OPEN, stream1.state());
        assertEquals(State.CLOSED, stream2.state());
        assertEquals(State.OPEN, remoteStream.state());
        assertEquals(3, client.local().lastKnownStream());
        assertEquals(5, client.local().lastStreamCreated());
        // The remote endpoint must not be affected by a received GOAWAY frame.
        assertEquals(-1, client.remote().lastKnownStream());
        assertEquals(State.OPEN, remoteStream.state());
    }

    @Test
    public void goAwaySentShouldCloseStreamsGreaterThanLastStream() throws Exception {
        Http2Stream stream1 = server.remote().createStream(3).open(false);
        Http2Stream stream2 = server.remote().createStream(5).open(false);
        Http2Stream localStream = server.local().createStream(4).open(false);

        server.goAwaySent(3, 8, null);

        assertEquals(State.OPEN, stream1.state());
        assertEquals(State.CLOSED, stream2.state());

        assertEquals(3, server.remote().lastKnownStream());
        assertEquals(5, server.remote().lastStreamCreated());
        // The local endpoint must not be affected by a sent GOAWAY frame.
        assertEquals(-1, server.local().lastKnownStream());
        assertEquals(State.OPEN, localStream.state());
    }

    @Test
    public void serverCreateStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.local().createStream(2).open(false);
        assertEquals(2, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(1, server.numActiveStreams());
        assertEquals(2, server.local().lastStreamCreated());

        stream = server.local().createStream(4).open(true);
        assertEquals(4, stream.id());
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(2, server.numActiveStreams());
        assertEquals(4, server.local().lastStreamCreated());

        stream = server.remote().createStream(3).open(true);
        assertEquals(3, stream.id());
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(3, server.numActiveStreams());
        assertEquals(3, server.remote().lastStreamCreated());

        stream = server.remote().createStream(5).open(false);
        assertEquals(5, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(4, server.numActiveStreams());
        assertEquals(5, server.remote().lastStreamCreated());
    }

    @Test
    public void clientCreateStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = client.remote().createStream(2).open(false);
        assertEquals(2, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(1, client.numActiveStreams());
        assertEquals(2, client.remote().lastStreamCreated());

        stream = client.remote().createStream(4).open(true);
        assertEquals(4, stream.id());
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(2, client.numActiveStreams());
        assertEquals(4, client.remote().lastStreamCreated());

        stream = client.local().createStream(3).open(true);
        assertEquals(3, stream.id());
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(3, client.numActiveStreams());
        assertEquals(3, client.local().lastStreamCreated());

        stream = client.local().createStream(5).open(false);
        assertEquals(5, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(4, client.numActiveStreams());
        assertEquals(5, client.local().lastStreamCreated());
    }

    @Test
    public void serverReservePushStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3).open(true);
        Http2Stream pushStream = server.local().reservePushStream(2, stream);
        assertEquals(2, pushStream.id());
        assertEquals(State.RESERVED_LOCAL, pushStream.state());
        assertEquals(1, server.numActiveStreams());
        assertEquals(2, server.local().lastStreamCreated());
    }

    @Test
    public void clientReservePushStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3).open(true);
        Http2Stream pushStream = server.local().reservePushStream(4, stream);
        assertEquals(4, pushStream.id());
        assertEquals(State.RESERVED_LOCAL, pushStream.state());
        assertEquals(1, server.numActiveStreams());
        assertEquals(4, server.local().lastStreamCreated());
    }

    @Test(expected = Http2Exception.class)
    public void newStreamBehindExpectedShouldThrow() throws Http2Exception {
        server.local().createStream(0).open(true);
    }

    @Test(expected = Http2Exception.class)
    public void newStreamNotForServerShouldThrow() throws Http2Exception {
        server.local().createStream(11).open(true);
    }

    @Test(expected = Http2Exception.class)
    public void newStreamNotForClientShouldThrow() throws Http2Exception {
        client.local().createStream(10).open(true);
    }

    @Test(expected = Http2Exception.class)
    public void maxAllowedStreamsExceededShouldThrow() throws Http2Exception {
        server.local().maxActiveStreams(0);
        server.local().createStream(2).open(true);
    }

    @Test(expected = Http2Exception.class)
    public void reserveWithPushDisallowedShouldThrow() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3).open(true);
        server.remote().allowPushTo(false);
        server.local().reservePushStream(2, stream);
    }

    @Test(expected = Http2Exception.class)
    public void goAwayReceivedShouldDisallowCreation() throws Http2Exception {
        server.goAwayReceived(0, 1L, Unpooled.EMPTY_BUFFER);
        server.remote().createStream(3).open(true);
    }

    @Test
    public void closeShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3).open(true);
        stream.close();
        assertEquals(State.CLOSED, stream.state());
        assertEquals(0, server.numActiveStreams());
    }

    @Test
    public void closeLocalWhenOpenShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3).open(false);
        stream.closeLocalSide();
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(1, server.numActiveStreams());
    }

    @Test
    public void closeRemoteWhenOpenShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3).open(false);
        stream.closeRemoteSide();
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(1, server.numActiveStreams());
    }

    @Test
    public void closeOnlyOpenSideShouldClose() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3).open(true);
        stream.closeLocalSide();
        assertEquals(State.CLOSED, stream.state());
        assertEquals(0, server.numActiveStreams());
    }

    @SuppressWarnings("NumericOverflow")
    @Test(expected = Http2Exception.class)
    public void localStreamInvalidStreamIdShouldThrow() throws Http2Exception {
        client.local().createStream(Integer.MAX_VALUE + 2).open(false);
    }

    @SuppressWarnings("NumericOverflow")
    @Test(expected = Http2Exception.class)
    public void remoteStreamInvalidStreamIdShouldThrow() throws Http2Exception {
        client.remote().createStream(Integer.MAX_VALUE + 1).open(false);
    }

    @Test
    public void localStreamCanDependUponIdleStream() throws Http2Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        streamA.setPriority(3, MIN_WEIGHT, true);
        verifyDependUponIdleStream(streamA, client.stream(3), client.local());
    }

    @Test
    public void remoteStreamCanDependUponIdleStream() throws Http2Exception {
        Http2Stream streamA = client.remote().createStream(2).open(false);
        streamA.setPriority(4, MIN_WEIGHT, true);
        verifyDependUponIdleStream(streamA, client.stream(4), client.remote());
    }

    @Test
    public void prioritizeShouldUseDefaults() throws Exception {
        Http2Stream stream = client.local().createStream(1).open(false);
        assertEquals(1, client.connectionStream().numChildren());
        assertEquals(2, client.connectionStream().prioritizableForTree());
        assertEquals(stream, child(client.connectionStream(), 1));
        assertEquals(DEFAULT_PRIORITY_WEIGHT, stream.weight());
        assertEquals(0, stream.parent().id());
        assertEquals(0, stream.numChildren());
    }

    @Test
    public void reprioritizeWithNoChangeShouldDoNothing() throws Exception {
        Http2Stream stream = client.local().createStream(1).open(false);
        stream.setPriority(0, DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(1, client.connectionStream().numChildren());
        assertEquals(2, client.connectionStream().prioritizableForTree());
        assertEquals(stream, child(client.connectionStream(), 1));
        assertEquals(DEFAULT_PRIORITY_WEIGHT, stream.weight());
        assertEquals(0, stream.parent().id());
        assertEquals(0, stream.numChildren());
    }

    @Test
    public void insertExclusiveShouldAddNewLevel() throws Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, client.numActiveStreams());

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(5, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = child(p, streamA.id());
        assertNotNull(p);
        assertEquals(0, p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(4, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = child(p, streamD.id());
        assertNotNull(p);
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(2, p.numChildren());
        assertEquals(3, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = child(p, streamB.id());
        assertNotNull(p);
        assertEquals(streamD.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamC.id());
        assertNotNull(p);
        assertEquals(streamD.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void existingChildMadeExclusiveShouldNotCreateTreeCycle() throws Http2Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Stream C is already dependent on Stream A, but now make that an exclusive dependency
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, client.numActiveStreams());

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(5, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = child(p, streamA.id());
        assertNotNull(p);
        assertEquals(0, p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(4, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = child(p, streamC.id());
        assertNotNull(p);
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(2, p.numChildren());
        assertEquals(3, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = child(p, streamB.id());
        assertNotNull(p);
        assertEquals(streamC.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamD.id());
        assertNotNull(p);
        assertEquals(streamC.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void newExclusiveChildShouldUpdateOldParentCorrectly() throws Http2Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);
        Http2Stream streamE = client.local().createStream(9).open(false);
        Http2Stream streamF = client.local().createStream(11).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamF.setPriority(streamE.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // F is now going to be exclusively dependent on A, after this we should check that stream E
        // prioritizableForTree is not over decremented.
        streamF.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(6, client.numActiveStreams());

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(2, p.numChildren());
        assertEquals(7, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = child(p, streamE.id());
        assertNotNull(p);
        assertEquals(0, p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamA.id());
        assertNotNull(p);
        assertEquals(0, p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(5, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = child(p, streamF.id());
        assertNotNull(p);
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(2, p.numChildren());
        assertEquals(4, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = child(p, streamB.id());
        assertNotNull(p);
        assertEquals(streamF.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamC.id());
        assertNotNull(p);
        assertEquals(streamF.id(), p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(2, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4
        p = child(p, streamD.id());
        assertNotNull(p);
        assertEquals(streamC.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void weightChangeWithNoTreeChangeShouldNotifyListeners() throws Http2Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, client.numActiveStreams());

        short oldWeight = streamD.weight();
        short newWeight = (short) (oldWeight + 1);
        reset(clientListener);
        streamD.setPriority(streamD.parent().id(), newWeight, false);
        verify(clientListener).onWeightChanged(eq(streamD), eq(oldWeight));
        assertEquals(streamD.weight(), newWeight);
        verify(clientListener, never()).onPriorityTreeParentChanging(any(Http2Stream.class),
                any(Http2Stream.class));
        verify(clientListener, never()).onPriorityTreeParentChanged(any(Http2Stream.class),
                any(Http2Stream.class));
        assertEquals(5, client.connectionStream().prioritizableForTree());
        assertEquals(4, streamA.prioritizableForTree());
        assertEquals(1, streamB.prioritizableForTree());
        assertEquals(1, streamC.prioritizableForTree());
        assertEquals(3, streamD.prioritizableForTree());
    }

    @Test
    public void sameNodeDependentShouldNotStackOverflowNorChangePrioritizableForTree() throws Http2Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        boolean[] exclusive = new boolean[] {true, false};
        short[] weights = new short[] { DEFAULT_PRIORITY_WEIGHT, 100, 200, streamD.weight() };

        assertEquals(4, client.numActiveStreams());

        Http2Stream connectionStream = client.connectionStream();
        assertEquals(5, connectionStream.prioritizableForTree());
        assertEquals(4, streamA.prioritizableForTree());
        assertEquals(1, streamB.prioritizableForTree());
        assertEquals(1, streamC.prioritizableForTree());
        assertEquals(3, streamD.prioritizableForTree());

        // The goal is to call setPriority with the same parent and vary the parameters
        // we were at one point adding a circular depends to the tree and then throwing
        // a StackOverflow due to infinite recursive operation.
        for (int j = 0; j < weights.length; ++j) {
            for (int i = 0; i < exclusive.length; ++i) {
                streamD.setPriority(streamA.id(), weights[j], exclusive[i]);
                assertEquals(5, connectionStream.prioritizableForTree());
                assertEquals(4, streamA.prioritizableForTree());
                assertEquals(1, streamB.prioritizableForTree());
                assertEquals(1, streamC.prioritizableForTree());
                assertEquals(3, streamD.prioritizableForTree());
            }
        }
    }

    @Test
    public void multipleCircularDependencyShouldUpdatePrioritizable() throws Http2Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, client.numActiveStreams());

        Http2Stream connectionStream = client.connectionStream();
        assertEquals(5, connectionStream.prioritizableForTree());
        assertEquals(4, streamA.prioritizableForTree());
        assertEquals(1, streamB.prioritizableForTree());
        assertEquals(1, streamC.prioritizableForTree());
        assertEquals(3, streamD.prioritizableForTree());

        // Bring B to the root
        streamA.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, true);
        assertEquals(5, connectionStream.prioritizableForTree());
        assertEquals(3, streamA.prioritizableForTree());
        assertEquals(4, streamB.prioritizableForTree());
        assertEquals(1, streamC.prioritizableForTree());
        assertEquals(2, streamD.prioritizableForTree());

        // Move all streams to be children of B
        streamC.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(5, connectionStream.prioritizableForTree());
        assertEquals(1, streamA.prioritizableForTree());
        assertEquals(4, streamB.prioritizableForTree());
        assertEquals(1, streamC.prioritizableForTree());
        assertEquals(1, streamD.prioritizableForTree());

        // Move A back to the root
        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);
        assertEquals(5, connectionStream.prioritizableForTree());
        assertEquals(4, streamA.prioritizableForTree());
        assertEquals(3, streamB.prioritizableForTree());
        assertEquals(1, streamC.prioritizableForTree());
        assertEquals(1, streamD.prioritizableForTree());

        // Move all streams to be children of A
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(5, connectionStream.prioritizableForTree());
        assertEquals(4, streamA.prioritizableForTree());
        assertEquals(1, streamB.prioritizableForTree());
        assertEquals(1, streamC.prioritizableForTree());
        assertEquals(1, streamD.prioritizableForTree());
    }

    @Test
    public void removeWithPrioritizableDependentsShouldNotRestructureTree() throws Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Default removal policy will cause it to be removed immediately.
        streamB.close();

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(4, p.prioritizableForTree());
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = child(p, streamA.id());
        assertNotNull(p);
        assertEquals(3, p.prioritizableForTree());
        assertEquals(0, p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = child(p, streamB.id());
        assertNotNull(p);
        assertEquals(2, p.prioritizableForTree());
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = child(p, streamC.id());
        assertNotNull(p);
        assertEquals(1, p.prioritizableForTree());
        assertEquals(streamB.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamD.id());
        assertNotNull(p);
        assertEquals(1, p.prioritizableForTree());
        assertEquals(streamB.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void closeWithNoPrioritizableDependentsShouldRestructureTree() throws Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);
        Http2Stream streamE = client.local().createStream(9).open(false);
        Http2Stream streamF = client.local().createStream(11).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamE.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamF.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Close internal nodes, leave 1 leaf node open, and ensure part of the tree (D & F) is cleaned up
        streamA.close();
        streamB.close();
        streamC.close();
        streamD.close();
        streamF.close();

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(2, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = child(p, streamA.id());
        assertNotNull(p);
        assertEquals(0, p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = child(p, streamB.id());
        assertNotNull(p);
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = child(p, streamC.id());
        assertNotNull(p);
        assertEquals(streamB.id(), p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4
        p = child(p, streamE.id());
        assertNotNull(p);
        assertEquals(streamC.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
    }

    @Test
    public void priorityChangeWithNoPrioritizableDependentsShouldRestructureTree() throws Exception {
        Http2Stream streamA = client.local().createStream(1).open(false);
        Http2Stream streamB = client.local().createStream(3).open(false);
        Http2Stream streamC = client.local().createStream(5).open(false);
        Http2Stream streamD = client.local().createStream(7).open(false);
        Http2Stream streamE = client.local().createStream(9).open(false);
        Http2Stream streamF = client.local().createStream(11).open(false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamE.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamF.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Leave leaf nodes open (E & F)
        streamA.close();
        streamB.close();
        streamC.close();
        streamD.close();

        // Move F to depend on C, this should close D
        streamF.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(3, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = child(p, streamA.id());
        assertNotNull(p);
        assertEquals(0, p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(2, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = child(p, streamB.id());
        assertNotNull(p);
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(2, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = child(p, streamC.id());
        assertNotNull(p);
        assertEquals(streamB.id(), p.parent().id());
        assertEquals(2, p.numChildren());
        assertEquals(2, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4
        p = child(p, streamE.id());
        assertNotNull(p);
        assertEquals(streamC.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        p = child(p.parent(), streamF.id());
        assertNotNull(p);
        assertEquals(streamC.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
    }

    @Test
    public void circularDependencyShouldRestructureTree() throws Exception {
        // Using example from http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-5.3.3
        // Initialize all the nodes
        Http2Stream streamA = client.local().createStream(1).open(false);
        verifyParentChanged(streamA, null);
        Http2Stream streamB = client.local().createStream(3).open(false);
        verifyParentChanged(streamB, null);
        Http2Stream streamC = client.local().createStream(5).open(false);
        verifyParentChanged(streamC, null);
        Http2Stream streamD = client.local().createStream(7).open(false);
        verifyParentChanged(streamD, null);
        Http2Stream streamE = client.local().createStream(9).open(false);
        verifyParentChanged(streamE, null);
        Http2Stream streamF = client.local().createStream(11).open(false);
        verifyParentChanged(streamF, null);

        // Build the tree
        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamB), anyShort());
        verifyParentChanged(streamB, client.connectionStream());
        verifyParentChanging(streamB, client.connectionStream());

        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamC), anyShort());
        verifyParentChanged(streamC, client.connectionStream());
        verifyParentChanging(streamC, client.connectionStream());

        streamD.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamD), anyShort());
        verifyParentChanged(streamD, client.connectionStream());
        verifyParentChanging(streamD, client.connectionStream());

        streamE.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamE), anyShort());
        verifyParentChanged(streamE, client.connectionStream());
        verifyParentChanging(streamE, client.connectionStream());

        streamF.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamF), anyShort());
        verifyParentChanged(streamF, client.connectionStream());
        verifyParentChanging(streamF, client.connectionStream());

        assertEquals(6, client.numActiveStreams());

        // Non-exclusive re-prioritization of a->d.
        reset(clientListener);
        streamA.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamA), anyShort());
        verifyParentChanging(Arrays.asList(streamD, streamA), Arrays.asList(client.connectionStream(), streamD));
        verifyParentsChanged(Arrays.asList(streamD, streamA), Arrays.asList(streamC, client.connectionStream()));

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(7, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = child(p, streamD.id());
        assertNotNull(p);
        assertEquals(2, p.numChildren());
        assertEquals(6, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = child(p, streamF.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamA.id());
        assertNotNull(p);
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = child(p, streamB.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamC.id());
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(2, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4;
        p = child(p, streamE.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void circularDependencyWithExclusiveShouldRestructureTree() throws Exception {
        // Using example from http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-5.3.3
        // Initialize all the nodes
        Http2Stream streamA = client.local().createStream(1).open(false);
        verifyParentChanged(streamA, null);
        Http2Stream streamB = client.local().createStream(3).open(false);
        verifyParentChanged(streamB, null);
        Http2Stream streamC = client.local().createStream(5).open(false);
        verifyParentChanged(streamC, null);
        Http2Stream streamD = client.local().createStream(7).open(false);
        verifyParentChanged(streamD, null);
        Http2Stream streamE = client.local().createStream(9).open(false);
        verifyParentChanged(streamE, null);
        Http2Stream streamF = client.local().createStream(11).open(false);
        verifyParentChanged(streamF, null);

        // Build the tree
        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamB), anyShort());
        verifyParentChanged(streamB, client.connectionStream());
        verifyParentChanging(streamB, client.connectionStream());

        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamC), anyShort());
        verifyParentChanged(streamC, client.connectionStream());
        verifyParentChanging(streamC, client.connectionStream());

        streamD.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamD), anyShort());
        verifyParentChanged(streamD, client.connectionStream());
        verifyParentChanging(streamD, client.connectionStream());

        streamE.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamE), anyShort());
        verifyParentChanged(streamE, client.connectionStream());
        verifyParentChanging(streamE, client.connectionStream());

        streamF.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);
        verify(clientListener, never()).onWeightChanged(eq(streamF), anyShort());
        verifyParentChanged(streamF, client.connectionStream());
        verifyParentChanging(streamF, client.connectionStream());

        assertEquals(6, client.numActiveStreams());

        // Exclusive re-prioritization of a->d.
        reset(clientListener);
        streamA.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, true);
        verify(clientListener, never()).onWeightChanged(eq(streamA), anyShort());
        verifyParentChanging(Arrays.asList(streamD, streamA, streamF),
                             Arrays.asList(client.connectionStream(), streamD, streamA));
        verifyParentsChanged(Arrays.asList(streamD, streamA, streamF),
                             Arrays.asList(streamC, client.connectionStream(), streamD));

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(7, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = child(p, streamD.id());
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(6, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = child(p, streamA.id());
        assertNotNull(p);
        assertEquals(3, p.numChildren());
        assertEquals(5, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = child(p, streamB.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamF.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = child(p.parent(), streamC.id());
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(2, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4;
        p = child(p, streamE.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(1, p.prioritizableForTree());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    /**
     * We force {@link #clientListener} methods to all throw a {@link RuntimeException} and verify the following:
     * <ol>
     * <li>all listener methods are called for both {@link #clientListener} and {@link #clientListener2}</li>
     * <li>{@link #clientListener2} is notified after {@link #clientListener}</li>
     * <li>{@link #clientListener2} methods are all still called despite {@link #clientListener}'s
     * method throwing a {@link RuntimeException}</li>
     * </ol>
     */
    @Test
    public void listenerThrowShouldNotPreventOtherListenersFromBeingNotified() throws Http2Exception {
        final boolean[] calledArray = new boolean[128];
        // The following setup will ensure that clienListener throws exceptions, and marks a value in an array
        // such that clientListener2 will verify that is is set or fail the test.
        int methodIndex = 0;
        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamAdded(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamAdded(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamActive(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamActive(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamHalfClosed(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamHalfClosed(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamClosed(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamClosed(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamRemoved(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamRemoved(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onPriorityTreeParentChanged(any(Http2Stream.class), any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onPriorityTreeParentChanged(any(Http2Stream.class), any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onPriorityTreeParentChanging(any(Http2Stream.class), any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onPriorityTreeParentChanging(any(Http2Stream.class), any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onWeightChanged(any(Http2Stream.class), anyShort());
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onWeightChanged(any(Http2Stream.class), anyShort());

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onGoAwaySent(anyInt(), anyLong(), any(ByteBuf.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onGoAwaySent(anyInt(), anyLong(), any(ByteBuf.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onGoAwayReceived(anyInt(), anyLong(), any(ByteBuf.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onGoAwayReceived(anyInt(), anyLong(), any(ByteBuf.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamAdded(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamAdded(any(Http2Stream.class));

        // Now we add clienListener2 and exercise all listener functionality
        try {
            client.addListener(clientListener2);
            Http2Stream stream = client.local().createStream(3);
            verify(clientListener).onStreamAdded(any(Http2Stream.class));
            verify(clientListener2).onStreamAdded(any(Http2Stream.class));

            stream.open(false);
            verify(clientListener).onStreamActive(any(Http2Stream.class));
            verify(clientListener2).onStreamActive(any(Http2Stream.class));

            stream.setPriority(0, (short) (stream.weight() + 1), true);
            verify(clientListener).onWeightChanged(any(Http2Stream.class), anyShort());
            verify(clientListener2).onWeightChanged(any(Http2Stream.class), anyShort());
            verify(clientListener).onPriorityTreeParentChanged(any(Http2Stream.class),
                    any(Http2Stream.class));
            verify(clientListener2).onPriorityTreeParentChanged(any(Http2Stream.class),
                    any(Http2Stream.class));
            verify(clientListener).onPriorityTreeParentChanging(any(Http2Stream.class),
                    any(Http2Stream.class));
            verify(clientListener2).onPriorityTreeParentChanging(any(Http2Stream.class),
                    any(Http2Stream.class));

            stream.closeLocalSide();
            verify(clientListener).onStreamHalfClosed(any(Http2Stream.class));
            verify(clientListener2).onStreamHalfClosed(any(Http2Stream.class));

            stream.close();
            verify(clientListener).onStreamClosed(any(Http2Stream.class));
            verify(clientListener2).onStreamClosed(any(Http2Stream.class));
            verify(clientListener).onStreamRemoved(any(Http2Stream.class));
            verify(clientListener2).onStreamRemoved(any(Http2Stream.class));

            client.goAwaySent(client.connectionStream().id(), Http2Error.INTERNAL_ERROR.code(), Unpooled.EMPTY_BUFFER);
            verify(clientListener).onGoAwaySent(anyInt(), anyLong(), any(ByteBuf.class));
            verify(clientListener2).onGoAwaySent(anyInt(), anyLong(), any(ByteBuf.class));

            client.goAwayReceived(client.connectionStream().id(),
                    Http2Error.INTERNAL_ERROR.code(), Unpooled.EMPTY_BUFFER);
            verify(clientListener).onGoAwayReceived(anyInt(), anyLong(), any(ByteBuf.class));
            verify(clientListener2).onGoAwayReceived(anyInt(), anyLong(), any(ByteBuf.class));
        } finally {
            client.removeListener(clientListener2);
        }
    }

    private static final class ListenerExceptionThrower implements Answer<Void> {
        private static final RuntimeException FAKE_EXCEPTION = new RuntimeException("Fake Exception");
        private final boolean[] array;
        private final int index;

        public ListenerExceptionThrower(boolean[] array, int index) {
            this.array = array;
            this.index = index;
        }

        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
            array[index] = true;
            throw FAKE_EXCEPTION;
        }
    }

    private static final class ListenerVerifyCallAnswer implements Answer<Void> {
        private final boolean[] array;
        private final int index;

        public ListenerVerifyCallAnswer(boolean[] array, int index) {
            this.array = array;
            this.index = index;
        }

        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
            assertTrue(array[index]);
            return null;
        }
    }

    private void verifyParentChanging(List<Http2Stream> expectedArg1, List<Http2Stream> expectedArg2) {
        assertSame(expectedArg1.size(), expectedArg2.size());
        ArgumentCaptor<Http2Stream> arg1Captor = ArgumentCaptor.forClass(Http2Stream.class);
        ArgumentCaptor<Http2Stream> arg2Captor = ArgumentCaptor.forClass(Http2Stream.class);
        verify(clientListener, times(expectedArg1.size())).onPriorityTreeParentChanging(arg1Captor.capture(),
                arg2Captor.capture());
        List<Http2Stream> capturedArg1 = arg1Captor.getAllValues();
        List<Http2Stream> capturedArg2 = arg2Captor.getAllValues();
        assertSame(capturedArg1.size(), capturedArg2.size());
        assertSame(capturedArg1.size(), expectedArg1.size());
        for (int i = 0; i < capturedArg1.size(); ++i) {
            assertEquals(expectedArg1.get(i), capturedArg1.get(i));
            assertEquals(expectedArg2.get(i), capturedArg2.get(i));
        }
    }

    private void verifyParentsChanged(List<Http2Stream> expectedArg1, List<Http2Stream> expectedArg2) {
        assertSame(expectedArg1.size(), expectedArg2.size());
        ArgumentCaptor<Http2Stream> arg1Captor = ArgumentCaptor.forClass(Http2Stream.class);
        ArgumentCaptor<Http2Stream> arg2Captor = ArgumentCaptor.forClass(Http2Stream.class);
        verify(clientListener, times(expectedArg1.size())).onPriorityTreeParentChanged(arg1Captor.capture(),
                arg2Captor.capture());
        List<Http2Stream> capturedArg1 = arg1Captor.getAllValues();
        List<Http2Stream> capturedArg2 = arg2Captor.getAllValues();
        assertSame(capturedArg1.size(), capturedArg2.size());
        assertSame(capturedArg1.size(), expectedArg1.size());
        for (int i = 0; i < capturedArg1.size(); ++i) {
            assertEquals(expectedArg1.get(i), capturedArg1.get(i));
            assertEquals(expectedArg2.get(i), capturedArg2.get(i));
        }
    }

    private static void verifyDependUponIdleStream(final Http2Stream streamA, Http2Stream streamB, Endpoint<?> endpoint)
            throws Http2Exception {
        assertNotNull(streamB);
        assertEquals(streamB.id(), endpoint.lastStreamCreated());
        assertEquals(State.IDLE, streamB.state());
        assertEquals(MIN_WEIGHT, streamA.weight());
        assertEquals(DEFAULT_PRIORITY_WEIGHT, streamB.weight());
        assertEquals(streamB, streamA.parent());
        assertEquals(1, streamB.numChildren());
        streamB.forEachChild(new Http2StreamVisitor() {
            @Override
            public boolean visit(Http2Stream stream) throws Http2Exception {
                assertEquals(streamA, stream);
                return false;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T streamEq(T stream) {
        return (T) (stream == null ? isNull(Http2Stream.class) : eq(stream));
    }

    private void verifyParentChanging(Http2Stream stream, Http2Stream newParent) {
        verify(clientListener).onPriorityTreeParentChanging(streamEq(stream), streamEq(newParent));
    }

    private void verifyParentChanged(Http2Stream stream, Http2Stream oldParent) {
        verify(clientListener).onPriorityTreeParentChanged(streamEq(stream), streamEq(oldParent));
    }
    private Http2Stream child(Http2Stream parent, final int id) {
        try {
            final AtomicReference<Http2Stream> streamReference = new AtomicReference<Http2Stream>();
            parent.forEachChild(new Http2StreamVisitor() {
                @Override
                public boolean visit(Http2Stream stream) throws Http2Exception {
                    if (stream.id() == id) {
                        streamReference.set(stream);
                        return false;
                    }
                    return true;
                }
            });
            return streamReference.get();
        } catch (Http2Exception e) {
            PlatformDependent.throwException(e);
            return null;
        }
    }
}

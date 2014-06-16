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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.netty.handler.codec.http2.Http2Stream.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DefaultHttp2Connection}.
 */
public class DefaultHttp2ConnectionTest {

    private DefaultHttp2Connection server;
    private DefaultHttp2Connection client;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        server = new DefaultHttp2Connection(true, false);
        client = new DefaultHttp2Connection(false, false);
    }

    @Test(expected = Http2Exception.class)
    public void getStreamOrFailWithoutStreamShouldFail() throws Http2Exception {
        server.requireStream(100);
    }

    @Test
    public void getStreamWithoutStreamShouldReturnNull() {
        assertNull(server.stream(100));
    }

    @Test
    public void serverCreateStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.local().createStream(2, false);
        assertEquals(2, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(1, server.activeStreams().size());
        assertEquals(2, server.local().lastStreamCreated());

        stream = server.local().createStream(4, true);
        assertEquals(4, stream.id());
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(2, server.activeStreams().size());
        assertEquals(4, server.local().lastStreamCreated());

        stream = server.remote().createStream(3, true);
        assertEquals(3, stream.id());
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(3, server.activeStreams().size());
        assertEquals(3, server.remote().lastStreamCreated());

        stream = server.remote().createStream(5, false);
        assertEquals(5, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(4, server.activeStreams().size());
        assertEquals(5, server.remote().lastStreamCreated());
    }

    @Test
    public void clientCreateStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = client.remote().createStream(2, false);
        assertEquals(2, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(1, client.activeStreams().size());
        assertEquals(2, client.remote().lastStreamCreated());

        stream = client.remote().createStream(4, true);
        assertEquals(4, stream.id());
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(2, client.activeStreams().size());
        assertEquals(4, client.remote().lastStreamCreated());

        stream = client.local().createStream(3, true);
        assertEquals(3, stream.id());
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(3, client.activeStreams().size());
        assertEquals(3, client.local().lastStreamCreated());

        stream = client.local().createStream(5, false);
        assertEquals(5, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(4, client.activeStreams().size());
        assertEquals(5, client.local().lastStreamCreated());
    }

    @Test
    public void serverReservePushStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        Http2Stream pushStream = server.local().reservePushStream(2, stream);
        assertEquals(2, pushStream.id());
        assertEquals(State.RESERVED_LOCAL, pushStream.state());
        assertEquals(1, server.activeStreams().size());
        assertEquals(2, server.local().lastStreamCreated());
    }

    @Test
    public void clientReservePushStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        Http2Stream pushStream = server.local().reservePushStream(4, stream);
        assertEquals(4, pushStream.id());
        assertEquals(State.RESERVED_LOCAL, pushStream.state());
        assertEquals(1, server.activeStreams().size());
        assertEquals(4, server.local().lastStreamCreated());
    }

    @Test(expected = Http2Exception.class)
    public void newStreamBehindExpectedShouldThrow() throws Http2Exception {
        server.local().createStream(0, true);
    }

    @Test(expected = Http2Exception.class)
    public void newStreamNotForServerShouldThrow() throws Http2Exception {
        server.local().createStream(11, true);
    }

    @Test(expected = Http2Exception.class)
    public void newStreamNotForClientShouldThrow() throws Http2Exception {
        client.local().createStream(10, true);
    }

    @Test(expected = Http2Exception.class)
    public void maxAllowedStreamsExceededShouldThrow() throws Http2Exception {
        server.local().maxStreams(0);
        server.local().createStream(2, true);
    }

    @Test(expected = Http2Exception.class)
    public void reserveWithPushDisallowedShouldThrow() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        server.remote().allowPushTo(false);
        server.local().reservePushStream(2, stream);
    }

    @Test(expected = Http2Exception.class)
    public void goAwayReceivedShouldDisallowCreation() throws Http2Exception {
        server.goAwayReceived();
        server.remote().createStream(3, true);
    }

    @Test
    public void closeShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        stream.close();
        assertEquals(State.CLOSED, stream.state());
        assertTrue(server.activeStreams().isEmpty());
    }

    @Test
    public void closeLocalWhenOpenShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, false);
        stream.closeLocalSide();
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(1, server.activeStreams().size());
    }

    @Test
    public void closeRemoteWhenOpenShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, false);
        stream.closeRemoteSide();
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(1, server.activeStreams().size());
    }

    @Test
    public void closeOnlyOpenSideShouldClose() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        stream.closeLocalSide();
        assertEquals(State.CLOSED, stream.state());
        assertTrue(server.activeStreams().isEmpty());
    }

    @Test
    public void prioritizeShouldUseDefaults() throws Exception {
        Http2Stream stream = client.local().createStream(1, false);
        assertEquals(1, client.connectionStream().numChildren());
        assertEquals(stream, client.connectionStream().child(1));
        assertEquals(DEFAULT_PRIORITY_WEIGHT, stream.weight());
        assertEquals(0, stream.parent().id());
        assertEquals(0, stream.numChildren());
    }

    @Test
    public void reprioritizeWithNoChangeShouldDoNothing() throws Exception {
        Http2Stream stream = client.local().createStream(1, false);
        stream.setPriority(0, DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(1, client.connectionStream().numChildren());
        assertEquals(stream, client.connectionStream().child(1));
        assertEquals(DEFAULT_PRIORITY_WEIGHT, stream.weight());
        assertEquals(0, stream.parent().id());
        assertEquals(0, stream.numChildren());
    }

    @Test
    public void insertExclusiveShouldAddNewLevel() throws Exception {
        Http2Stream streamA = client.local().createStream(1, false);
        Http2Stream streamB = client.local().createStream(3, false);
        Http2Stream streamC = client.local().createStream(5, false);
        Http2Stream streamD = client.local().createStream(7, false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, client.numActiveStreams());

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = p.child(streamA.id());
        assertNotNull(p);
        assertEquals(0, p.parent().id());
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = p.child(streamD.id());
        assertNotNull(p);
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = p.child(streamB.id());
        assertNotNull(p);
        assertEquals(streamD.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().child(streamC.id());
        assertNotNull(p);
        assertEquals(streamD.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void removeShouldRestructureTree() throws Exception {
        Http2Stream streamA = client.local().createStream(1, false);
        Http2Stream streamB = client.local().createStream(3, false);
        Http2Stream streamC = client.local().createStream(5, false);
        Http2Stream streamD = client.local().createStream(7, false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Default removal policy will cause it to be removed immediately.
        streamB.close();

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = p.child(streamA.id());
        assertNotNull(p);
        assertEquals(0, p.parent().id());
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = p.child(streamC.id());
        assertNotNull(p);
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().child(streamD.id());
        assertNotNull(p);
        assertEquals(streamA.id(), p.parent().id());
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void circularDependencyShouldRestructureTree() throws Exception {
        // Using example from http://tools.ietf.org/html/draft-ietf-httpbis-http2-12#section-5.3.3
        Http2Stream streamA = client.local().createStream(1, false);
        Http2Stream streamB = client.local().createStream(3, false);
        Http2Stream streamC = client.local().createStream(5, false);
        Http2Stream streamD = client.local().createStream(7, false);
        Http2Stream streamE = client.local().createStream(9, false);
        Http2Stream streamF = client.local().createStream(11, false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamE.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamF.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);

        assertEquals(6, client.numActiveStreams());

        // Non-exclusive re-prioritization of a->d.
        streamA.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = p.child(streamD.id());
        assertNotNull(p);
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = p.child(streamF.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().child(streamA.id());
        assertNotNull(p);
        assertEquals(2, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = p.child(streamB.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().child(streamC.id());
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4;
        p = p.child(streamE.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }

    @Test
    public void circularDependencyWithExclusiveShouldRestructureTree() throws Exception {
        // Using example from http://tools.ietf.org/html/draft-ietf-httpbis-http2-12#section-5.3.3
        // Although the expected output for the exclusive case has an error in the document. The
        // final dependency of C should be E (not F). This is fixed here.
        Http2Stream streamA = client.local().createStream(1, false);
        Http2Stream streamB = client.local().createStream(3, false);
        Http2Stream streamC = client.local().createStream(5, false);
        Http2Stream streamD = client.local().createStream(7, false);
        Http2Stream streamE = client.local().createStream(9, false);
        Http2Stream streamF = client.local().createStream(11, false);

        streamB.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamC.setPriority(streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamE.setPriority(streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        streamF.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);

        assertEquals(6, client.numActiveStreams());

        // Exclusive re-prioritization of a->d.
        streamA.setPriority(streamD.id(), DEFAULT_PRIORITY_WEIGHT, true);

        // Level 0
        Http2Stream p = client.connectionStream();
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 1
        p = p.child(streamD.id());
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 2
        p = p.child(streamA.id());
        assertNotNull(p);
        assertEquals(3, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 3
        p = p.child(streamB.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().child(streamF.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
        p = p.parent().child(streamC.id());
        assertNotNull(p);
        assertEquals(1, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());

        // Level 4;
        p = p.child(streamE.id());
        assertNotNull(p);
        assertEquals(0, p.numChildren());
        assertEquals(p.numChildren() * DEFAULT_PRIORITY_WEIGHT, p.totalChildWeights());
    }
}

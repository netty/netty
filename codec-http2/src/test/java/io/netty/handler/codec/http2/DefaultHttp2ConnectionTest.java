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

import static org.junit.Assert.assertEquals;
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
        Http2Stream stream = client.remote().createStream(2, true);
        Http2Stream pushStream = client.local().reservePushStream(3, stream);
        assertEquals(3, pushStream.id());
        assertEquals(State.RESERVED_LOCAL, pushStream.state());
        assertEquals(1, client.activeStreams().size());
        assertEquals(3, client.local().lastStreamCreated());
    }

    @Test(expected = Http2Exception.class)
    public void createStreamWithInvalidIdShouldThrow() throws Http2Exception {
        server.remote().createStream(1, true);
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
}

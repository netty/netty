/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.http2.draft10.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.connection.Http2Connection.Listener;
import io.netty.handler.codec.http2.draft10.connection.Http2Stream.State;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DefaultHttp2Connection}.
 */
public class DefaultHttp2ConnectionTest {

    @Mock
    private Listener listener;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ChannelFuture future;

    @Mock
    private ChannelPromise promise;

    private DefaultHttp2Connection server;
    private DefaultHttp2Connection client;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(ctx.writeAndFlush(any())).thenReturn(future);
        when(ctx.newSucceededFuture()).thenReturn(future);
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ChannelFutureListener listener =
                        (ChannelFutureListener) invocation.getArguments()[0];
                listener.operationComplete(future);
                return null;
            }
        }).when(future).addListener(any(ChannelFutureListener.class));

        server = new DefaultHttp2Connection(true);
        client = new DefaultHttp2Connection(false);

        server.addListener(listener);
        client.addListener(listener);
    }

    @Test(expected = Http2Exception.class)
    public void getStreamOrFailWithoutStreamShouldFail() throws Http2Exception {
        server.getStreamOrFail(100);
    }

    @Test
    public void getStreamWithoutStreamShouldReturnNull() {
        assertNull(server.getStream(100));
    }

    @Test
    public void serverCreateStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.local().createStream(2, 1, false);
        assertEquals(2, stream.getId());
        assertEquals(1, stream.getPriority());
        assertEquals(State.OPEN, stream.getState());
        assertEquals(1, server.getActiveStreams().size());
        assertEquals(2, server.local().getLastStreamCreated());
        verify(listener).streamCreated(2);

        stream = server.local().createStream(4, 256, true);
        assertEquals(4, stream.getId());
        assertEquals(256, stream.getPriority());
        assertEquals(State.HALF_CLOSED_LOCAL, stream.getState());
        assertEquals(2, server.getActiveStreams().size());
        assertEquals(4, server.local().getLastStreamCreated());
        verify(listener).streamCreated(4);

        stream = server.remote().createStream(3, Integer.MAX_VALUE, true);
        assertEquals(3, stream.getId());
        assertEquals(Integer.MAX_VALUE, stream.getPriority());
        assertEquals(State.HALF_CLOSED_REMOTE, stream.getState());
        assertEquals(3, server.getActiveStreams().size());
        assertEquals(3, server.remote().getLastStreamCreated());
        verify(listener).streamCreated(3);

        stream = server.remote().createStream(5, 1, false);
        assertEquals(5, stream.getId());
        assertEquals(1, stream.getPriority());
        assertEquals(State.OPEN, stream.getState());
        assertEquals(4, server.getActiveStreams().size());
        assertEquals(5, server.remote().getLastStreamCreated());
        verify(listener).streamCreated(5);
    }

    @Test
    public void clientCreateStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = client.remote().createStream(2, 1, false);
        assertEquals(2, stream.getId());
        assertEquals(1, stream.getPriority());
        assertEquals(State.OPEN, stream.getState());
        assertEquals(1, client.getActiveStreams().size());
        assertEquals(2, client.remote().getLastStreamCreated());
        verify(listener).streamCreated(2);

        stream = client.remote().createStream(4, 256, true);
        assertEquals(4, stream.getId());
        assertEquals(256, stream.getPriority());
        assertEquals(State.HALF_CLOSED_REMOTE, stream.getState());
        assertEquals(2, client.getActiveStreams().size());
        assertEquals(4, client.remote().getLastStreamCreated());
        verify(listener).streamCreated(4);

        stream = client.local().createStream(3, Integer.MAX_VALUE, true);
        assertEquals(3, stream.getId());
        assertEquals(Integer.MAX_VALUE, stream.getPriority());
        assertEquals(State.HALF_CLOSED_LOCAL, stream.getState());
        assertEquals(3, client.getActiveStreams().size());
        assertEquals(3, client.local().getLastStreamCreated());
        verify(listener).streamCreated(3);

        stream = client.local().createStream(5, 1, false);
        assertEquals(5, stream.getId());
        assertEquals(1, stream.getPriority());
        assertEquals(State.OPEN, stream.getState());
        assertEquals(4, client.getActiveStreams().size());
        assertEquals(5, client.local().getLastStreamCreated());
        verify(listener).streamCreated(5);
    }

    @Test
    public void serverReservePushStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, 1, true);
        Http2Stream pushStream = server.local().reservePushStream(2, stream);
        assertEquals(2, pushStream.getId());
        assertEquals(2, pushStream.getPriority());
        assertEquals(State.RESERVED_LOCAL, pushStream.getState());
        assertEquals(1, server.getActiveStreams().size());
        assertEquals(2, server.local().getLastStreamCreated());
        verify(listener).streamCreated(3);
        verify(listener).streamCreated(2);
    }

    @Test
    public void clientReservePushStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = client.remote().createStream(2, 1, true);
        Http2Stream pushStream = client.local().reservePushStream(3, stream);
        assertEquals(3, pushStream.getId());
        assertEquals(2, pushStream.getPriority());
        assertEquals(State.RESERVED_LOCAL, pushStream.getState());
        assertEquals(1, client.getActiveStreams().size());
        assertEquals(3, client.local().getLastStreamCreated());
        verify(listener).streamCreated(2);
        verify(listener).streamCreated(3);
    }

    @Test(expected = Http2Exception.class)
    public void createStreamWithInvalidIdShouldThrow() throws Http2Exception {
        server.remote().createStream(1, 1, true);
    }

    @Test(expected = Http2Exception.class)
    public void maxAllowedStreamsExceededShouldThrow() throws Http2Exception {
        server.local().setMaxStreams(0);
        server.local().createStream(2, 1, true);
    }

    @Test(expected = Http2Exception.class)
    public void invalidPriorityShouldThrow() throws Http2Exception {
        server.local().createStream(2, -1, true);
    }

    @Test(expected = Http2Exception.class)
    public void reserveWithPushDisallowedShouldThrow() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, 1, true);
        server.remote().setPushToAllowed(false);
        server.local().reservePushStream(2, stream);
    }

    @Test(expected = Http2Exception.class)
    public void goAwayReceivedShouldDisallowCreation() throws Http2Exception {
        server.goAwayReceived();
        server.remote().createStream(3, 1, true);
    }

    @Test
    public void activeStreamsShouldBeSortedByPriority() throws Http2Exception {
        server.local().createStream(2, 1, false);
        server.local().createStream(4, 256, true);
        server.remote().createStream(3, Integer.MAX_VALUE, true);
        server.remote().createStream(5, 1, false);
        List<Http2Stream> activeStreams = server.getActiveStreams();
        assertEquals(2, activeStreams.get(0).getId());
        assertEquals(5, activeStreams.get(1).getId());
        assertEquals(4, activeStreams.get(2).getId());
        assertEquals(3, activeStreams.get(3).getId());
    }

    @Test
    public void priorityChangeShouldReorderActiveStreams() throws Http2Exception {
        server.local().createStream(2, 1, false);
        server.local().createStream(4, 256, true);
        server.remote().createStream(3, Integer.MAX_VALUE, true);
        server.remote().createStream(5, 1, false);
        Http2Stream stream7 = server.remote().createStream(7, 1, false);
        server.remote().createStream(9, 1, false);

        // Make this this highest priority.
        stream7.setPriority(0);

        List<Http2Stream> activeStreams = server.getActiveStreams();
        assertEquals(7, activeStreams.get(0).getId());
        assertEquals(2, activeStreams.get(1).getId());
        assertEquals(5, activeStreams.get(2).getId());
        assertEquals(9, activeStreams.get(3).getId());
        assertEquals(4, activeStreams.get(4).getId());
        assertEquals(3, activeStreams.get(5).getId());
    }

    @Test
    public void closeShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, 1, true);
        stream.close(ctx, future);
        assertEquals(State.CLOSED, stream.getState());
        assertTrue(server.getActiveStreams().isEmpty());
        verify(listener).streamClosed(3);
    }

    @Test
    public void closeLocalWhenOpenShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, 1, false);
        stream.closeLocalSide(ctx, future);
        assertEquals(State.HALF_CLOSED_LOCAL, stream.getState());
        assertEquals(1, server.getActiveStreams().size());
        verify(listener, never()).streamClosed(3);
    }

    @Test
    public void closeRemoteWhenOpenShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, 1, false);
        stream.closeRemoteSide(ctx, future);
        assertEquals(State.HALF_CLOSED_REMOTE, stream.getState());
        assertEquals(1, server.getActiveStreams().size());
        verify(listener, never()).streamClosed(3);
    }

    @Test
    public void closeOnlyOpenSideShouldClose() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, 1, true);
        stream.closeLocalSide(ctx, future);
        assertEquals(State.CLOSED, stream.getState());
        assertTrue(server.getActiveStreams().isEmpty());
        verify(listener).streamClosed(3);
    }

    @Test
    public void sendGoAwayShouldCloseConnection() {
        server.sendGoAway(ctx, promise, null);
        verify(ctx).close(promise);
    }

    @Test
    public void sendGoAwayShouldCloseAfterConnectionInactive() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, 1, true);
        server.sendGoAway(ctx, promise, null);
        verify(ctx, never()).close(promise);

        // Now close the stream and verify that the context was closed too.
        stream.close(ctx, future);
        verify(ctx).close(promise);
    }
}

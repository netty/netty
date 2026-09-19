/*
 * Copyright 2026 The Netty Project
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the promised stream when a PUSH_PROMISE arrives on a parent stream for which we have already sent
 * RST_STREAM. Unlike {@link DefaultHttp2ConnectionDecoderTest} these tests drive real frame bytes through a real
 * {@link DefaultHttp2Connection}, so they observe the resulting stream state rather than which methods the decoder
 * called.
 */
public class Http2PushPromiseAfterResetTest {

    private static final int PARENT_STREAM_ID = 3;
    private static final int PROMISED_STREAM_ID = 2;

    private Http2Connection connection;
    private EmbeddedChannel channel;
    private Http2FrameInboundWriter inboundWriter;
    private Http2FrameWriter frameWriter;
    private RecordingFrameListener listener;

    private static final class RecordingFrameListener extends Http2FrameAdapter {
        final List<Integer> pushPromises = new ArrayList<Integer>();
        final List<Integer> headersSeen = new ArrayList<Integer>();

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                                  boolean endOfStream) {
            headersSeen.add(streamId);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                  int streamDependency, short weight, boolean exclusive, int padding,
                                  boolean endOfStream) {
            headersSeen.add(streamId);
        }
        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                      Http2Headers headers, int padding) {
            pushPromises.add(promisedStreamId);
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        connection = new DefaultHttp2Connection(false);
        listener = new RecordingFrameListener();

        frameWriter = Http2TestUtil.mockedFrameWriter();
        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        Http2ConnectionDecoder decoder =
                new DefaultHttp2ConnectionDecoder(connection, encoder, new DefaultHttp2FrameReader());
        Http2ConnectionHandler handler = new Http2ConnectionHandlerBuilder()
                .codec(decoder, encoder)
                .frameListener(listener)
                .build();

        channel = new EmbeddedChannel();
        inboundWriter = new Http2FrameInboundWriter(channel);
        channel.pipeline().addLast(handler);
        channel.pipeline().fireChannelActive();

        // The decoder discards everything until the peer's preface has been seen.
        inboundWriter.writeInboundSettings(new Http2Settings());
    }

    @AfterEach
    public void tearDown() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Opens a local stream and marks RST_STREAM as sent on it without closing it, which is the state
     * {@link Http2ConnectionHandler#resetStream} leaves the stream in until the RST_STREAM write completes.
     */
    private Http2Stream openAndResetParentStream() throws Http2Exception {
        Http2Stream parent = connection.local().createStream(PARENT_STREAM_ID, false);
        parent.resetSent();
        return parent;
    }

    private static Http2Headers request() {
        return new DefaultHttp2Headers().method("GET").scheme("https").authority("example.org").path("/pushed");
    }

    @Test
    public void discardedPromiseDoesNotOutliveItsParentStream() throws Exception {
        Http2Stream parent = openAndResetParentStream();

        inboundWriter.writePushPromise(PARENT_STREAM_ID, PROMISED_STREAM_ID, request(), 0);

        // RFC 9113, Section 5.1: the promise still takes effect on the promised stream even though we discard
        // the frame, so the promised id must be consumed and our id accounting must track the peer's.
        assertEquals(PROMISED_STREAM_ID, connection.remote().lastStreamCreated(),
                "PUSH_PROMISE on a reset parent must still reserve the promised stream");
        assertTrue(listener.pushPromises.isEmpty(), "a discarded PUSH_PROMISE must not reach the listener");

        // Nothing above the decoder was told this stream exists, so nothing above the decoder will ever reset it.
        // Once the exchange it belongs to is over it should be gone.
        parent.close();
        assertNull(connection.stream(PROMISED_STREAM_ID),
                "promised stream leaked: still registered after the parent stream closed");
    }

    @Test
    public void discardedPromisesDoNotConsumeTheStreamBudget() throws Exception {
        Http2Stream parent = openAndResetParentStream();

        int promisedStreamId = PROMISED_STREAM_ID;
        for (int i = 0; i < 10 * Http2CodecUtil.DEFAULT_MAX_RESERVED_STREAMS; i++) {
            inboundWriter.writePushPromise(PARENT_STREAM_ID, promisedStreamId, request(), 0);
            promisedStreamId += 2;
        }

        parent.close();
        assertEquals(0, connection.numActiveStreams());

        // None of those promises was ever surfaced, so a later push on a healthy parent should be unaffected.
        connection.local().createStream(PARENT_STREAM_ID + 2, false);
        inboundWriter.writePushPromise(PARENT_STREAM_ID + 2, promisedStreamId, request(), 0);

        assertNotNull(connection.stream(promisedStreamId),
                "server push is broken for the rest of the connection: the discarded promises exhausted "
                        + "maxActiveStreams + maxReservedStreams and are never released");
        assertEquals(singletonList(promisedStreamId), listener.pushPromises);
    }

    @Test
    public void promiseOnAParentThatIsNoLongerOpenForPushDoesNotKillTheConnection() throws Exception {
        Http2Stream parent = connection.local().createStream(PARENT_STREAM_ID, false);
        parent.closeRemoteSide();
        parent.resetSent();
        assertEquals(HALF_CLOSED_REMOTE, parent.state());

        // Promising after END_STREAM is out of spec, but we already reset the stream, so keep ignoring it instead
        // of reserving a stream the connection would refuse to create.
        inboundWriter.writePushPromise(PARENT_STREAM_ID, PROMISED_STREAM_ID, request(), 0);

        assertTrue(channel.isActive(), "connection was torn down by a PUSH_PROMISE we had already chosen to ignore");
        assertNull(connection.stream(PROMISED_STREAM_ID));
        assertTrue(listener.pushPromises.isEmpty());
    }

    @Test
    public void promiseOnAParentAlreadyRemovedStillConsumesThePromisedId() throws Exception {
        Http2Stream parent = openAndResetParentStream();
        parent.close();
        assertNull(connection.stream(PARENT_STREAM_ID));

        inboundWriter.writePushPromise(PARENT_STREAM_ID, PROMISED_STREAM_ID, request(), 0);

        // We can no longer tell that we reset the parent, but Section 5.1 lets us apply the same minimal
        // processing to any closed stream, so the promised id is consumed exactly as it is before the flush.
        assertEquals(PROMISED_STREAM_ID, connection.remote().lastStreamCreated());
        assertNull(connection.stream(PROMISED_STREAM_ID), "the declined promise must not stay reserved");
        assertTrue(listener.pushPromises.isEmpty());

        // The promise is declined on the promised stream. Resetting the closed parent instead would violate
        // Section 5.1 and let a peer drive one control frame out of us per promise it sends.
        verify(frameWriter).writeRstStream(any(ChannelHandlerContext.class), eq(PROMISED_STREAM_ID),
                eq(Http2Error.REFUSED_STREAM.code()), any(ChannelPromise.class));
        verify(frameWriter, never()).writeRstStream(any(ChannelHandlerContext.class), eq(PARENT_STREAM_ID),
                anyLong(), any(ChannelPromise.class));
        assertTrue(channel.isActive());
    }

    @Test
    public void pushedResponseAfterADeclinedPromiseIsNotSurfacedAsANewStream() throws Exception {
        Http2Stream parent = openAndResetParentStream();
        parent.close();

        inboundWriter.writePushPromise(PARENT_STREAM_ID, PROMISED_STREAM_ID, request(), 0);
        inboundWriter.writeInboundHeaders(PROMISED_STREAM_ID, new DefaultHttp2Headers().status("200"), 0, true);

        // Because the id was consumed, the pushed response is recognised as belonging to a promise we already
        // declined rather than minted as a brand new server-initiated stream and handed to the application.
        assertTrue(listener.headersSeen.isEmpty(),
                "pushed response surfaced with no preceding onPushPromiseRead: " + listener.headersSeen);
    }
}

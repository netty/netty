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

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_WINDOW_SIZE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DefaultHttp2InboundFlowController}.
 */
public class DefaultHttp2InboundFlowControllerTest {
    private static final int STREAM_ID = 1;

    private DefaultHttp2InboundFlowController controller;

    @Mock
    private ByteBuf buffer;

    @Mock
    private Http2FrameWriter frameWriter;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ChannelPromise promise;

    private DefaultHttp2Connection connection;

    @Before
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        when(ctx.newPromise()).thenReturn(promise);

        connection = new DefaultHttp2Connection(false);
        controller = new DefaultHttp2InboundFlowController(connection, frameWriter);

        connection.local().createStream(STREAM_ID, false);
    }

    @Test
    public void dataFrameShouldBeAccepted() throws Http2Exception {
        applyFlowControl(STREAM_ID, 10, 0, false);
        verifyWindowUpdateNotSent();
    }

    @Test
    public void windowUpdateShouldSendOnceBytesReturned() throws Http2Exception {
        int dataSize = DEFAULT_WINDOW_SIZE / 2 + 1;
        applyFlowControl(STREAM_ID, dataSize, 0, false);

        // Return only a few bytes and verify that the WINDOW_UPDATE hasn't been sent.
        returnProcessedBytes(STREAM_ID, 10);
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, dataSize);

        // Return the rest and verify the WINDOW_UPDATE is sent.
        returnProcessedBytes(STREAM_ID, dataSize - 10);
        verifyWindowUpdateSent(STREAM_ID, dataSize);
    }

    @Test(expected = Http2Exception.class)
    public void connectionFlowControlExceededShouldThrow() throws Http2Exception {
        // Window exceeded because of the padding.
        applyFlowControl(STREAM_ID, DEFAULT_WINDOW_SIZE, 1, true);
    }

    @Test
    public void windowUpdateShouldNotBeSentAfterEndOfStream() throws Http2Exception {
        int dataSize = DEFAULT_WINDOW_SIZE / 2 + 1;
        int newWindow = DEFAULT_WINDOW_SIZE - dataSize;
        int windowDelta = DEFAULT_WINDOW_SIZE - newWindow;

        // Set end-of-stream on the frame, so no window update will be sent for the stream.
        applyFlowControl(STREAM_ID, dataSize, 0, true);
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, windowDelta);
        verifyWindowUpdateNotSent(STREAM_ID);

        returnProcessedBytes(STREAM_ID, dataSize);
        verifyWindowUpdateNotSent(STREAM_ID);
    }

    @Test
    public void halfWindowRemainingShouldUpdateAllWindows() throws Http2Exception {
        int dataSize = DEFAULT_WINDOW_SIZE / 2 + 1;
        int initialWindowSize = DEFAULT_WINDOW_SIZE;
        int windowDelta = getWindowDelta(initialWindowSize, initialWindowSize, dataSize);

        // Don't set end-of-stream so we'll get a window update for the stream as well.
        applyFlowControl(STREAM_ID, dataSize, 0, false);
        returnProcessedBytes(STREAM_ID, dataSize);
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, windowDelta);
        verifyWindowUpdateSent(STREAM_ID, windowDelta);
    }

    @Test
    public void initialWindowUpdateShouldAllowMoreFrames() throws Http2Exception {
        // Send a frame that takes up the entire window.
        int initialWindowSize = DEFAULT_WINDOW_SIZE;
        applyFlowControl(STREAM_ID, initialWindowSize, 0, false);
        assertEquals(0, window(STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));
        returnProcessedBytes(STREAM_ID, initialWindowSize);
        assertEquals(initialWindowSize, window(STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));

        // Update the initial window size to allow another frame.
        int newInitialWindowSize = 2 * initialWindowSize;
        controller.initialInboundWindowSize(newInitialWindowSize);
        assertEquals(newInitialWindowSize, window(STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));

        // Clear any previous calls to the writer.
        reset(frameWriter);

        // Send the next frame and verify that the expected window updates were sent.
        applyFlowControl(STREAM_ID, initialWindowSize, 0, false);
        returnProcessedBytes(STREAM_ID, initialWindowSize);
        int delta = newInitialWindowSize - initialWindowSize;
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, newInitialWindowSize);
        verifyWindowUpdateSent(STREAM_ID, delta);
    }

    @Test
    public void connectionWindowShouldExpandWithNumberOfStreams() throws Http2Exception {
        // Create another stream
        int newStreamId = 3;
        connection.local().createStream(newStreamId, false);

        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));

        // Receive some data - this should cause the connection window to expand.
        int data1 = 50;
        int expectedMaxConnectionWindow = DEFAULT_WINDOW_SIZE * 2;
        applyFlowControl(STREAM_ID, data1, 0, false);
        verifyWindowUpdateNotSent(STREAM_ID);
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, DEFAULT_WINDOW_SIZE + data1);
        assertEquals(DEFAULT_WINDOW_SIZE - data1, window(STREAM_ID));
        assertEquals(expectedMaxConnectionWindow, window(CONNECTION_STREAM_ID));

        reset(frameWriter);

        // Close the new stream.
        connection.stream(newStreamId).close();

        // Read more data and verify that the stream window refreshes but the
        // connection window continues collapsing.
        int data2 = window(STREAM_ID);
        applyFlowControl(STREAM_ID, data2, 0, false);
        returnProcessedBytes(STREAM_ID, data2);
        verifyWindowUpdateSent(STREAM_ID, data2);
        verifyWindowUpdateNotSent(CONNECTION_STREAM_ID);
        assertEquals(DEFAULT_WINDOW_SIZE - data1, window(STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE * 2 - data2 , window(CONNECTION_STREAM_ID));

        reset(frameWriter);
        returnProcessedBytes(STREAM_ID, data1);
        verifyWindowUpdateNotSent(STREAM_ID);

        // Read enough data to cause a WINDOW_UPDATE for both the stream and connection and
        // verify the new maximum of the connection window.
        int data3 = window(STREAM_ID);
        applyFlowControl(STREAM_ID, data3, 0, false);
        returnProcessedBytes(STREAM_ID, data3);
        verifyWindowUpdateSent(STREAM_ID, DEFAULT_WINDOW_SIZE);
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, DEFAULT_WINDOW_SIZE
                - (DEFAULT_WINDOW_SIZE * 2 - (data2 + data3)));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));
    }

    private static int getWindowDelta(int initialSize, int windowSize, int dataSize) {
        int newWindowSize = windowSize - dataSize;
        return initialSize - newWindowSize;
    }

    private void applyFlowControl(int streamId, int dataSize, int padding, boolean endOfStream) throws Http2Exception {
        final ByteBuf buf = dummyData(dataSize);
        try {
            controller.applyFlowControl(ctx, streamId, buf, padding, endOfStream);
        } finally {
            buf.release();
        }
    }

    private static ByteBuf dummyData(int size) {
        final ByteBuf buffer = Unpooled.buffer(size);
        buffer.writerIndex(size);
        return buffer;
    }

    private void returnProcessedBytes(int streamId, int processedBytes) throws Http2Exception {
        connection.requireStream(streamId).garbageCollector().returnProcessedBytes(ctx, processedBytes);
    }

    private void verifyWindowUpdateSent(int streamId, int windowSizeIncrement) throws Http2Exception {
        verify(frameWriter).writeWindowUpdate(eq(ctx), eq(streamId), eq(windowSizeIncrement), eq(promise));
    }

    private void verifyWindowUpdateNotSent(int streamId) {
        verify(frameWriter, never()).writeWindowUpdate(eq(ctx), eq(streamId), anyInt(), eq(promise));
    }

    private void verifyWindowUpdateNotSent() {
        verify(frameWriter, never()).writeWindowUpdate(any(ChannelHandlerContext.class), anyInt(), anyInt(),
                any(ChannelPromise.class));
    }

    private int window(int streamId) {
        return connection.stream(streamId).inboundFlow().window();
    }
}

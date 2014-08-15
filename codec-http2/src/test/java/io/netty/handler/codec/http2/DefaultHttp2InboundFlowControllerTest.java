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
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.Http2InboundFlowController.FrameWriter;

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
    private FrameWriter frameWriter;

    private DefaultHttp2Connection connection;

    @Before
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        connection = new DefaultHttp2Connection(false);
        controller = new DefaultHttp2InboundFlowController(connection);

        connection.local().createStream(STREAM_ID, false);
    }

    @Test
    public void dataFrameShouldBeAccepted() throws Http2Exception {
        applyFlowControl(10, false);
        verifyWindowUpdateNotSent();
    }

    @Test(expected = Http2Exception.class)
    public void connectionFlowControlExceededShouldThrow() throws Http2Exception {
        applyFlowControl(DEFAULT_WINDOW_SIZE + 1, true);
    }

    @Test
    public void halfWindowRemainingShouldUpdateConnectionWindow() throws Http2Exception {
        int dataSize = DEFAULT_WINDOW_SIZE / 2 + 1;
        int newWindow = DEFAULT_WINDOW_SIZE - dataSize;
        int windowDelta = DEFAULT_WINDOW_SIZE - newWindow;

        // Set end-of-stream on the frame, so no window update will be sent for the stream.
        applyFlowControl(dataSize, true);
        verify(frameWriter).writeFrame(eq(CONNECTION_STREAM_ID), eq(windowDelta));
    }

    @Test
    public void halfWindowRemainingShouldUpdateAllWindows() throws Http2Exception {
        int dataSize = DEFAULT_WINDOW_SIZE / 2 + 1;
        int initialWindowSize = DEFAULT_WINDOW_SIZE;
        int windowDelta = getWindowDelta(initialWindowSize, initialWindowSize, dataSize);

        // Don't set end-of-stream so we'll get a window update for the stream as well.
        applyFlowControl(dataSize, false);
        verify(frameWriter).writeFrame(eq(CONNECTION_STREAM_ID), eq(windowDelta));
        verify(frameWriter).writeFrame(eq(STREAM_ID), eq(windowDelta));
    }

    @Test
    public void initialWindowUpdateShouldAllowMoreFrames() throws Http2Exception {
        // Send a frame that takes up the entire window.
        int initialWindowSize = DEFAULT_WINDOW_SIZE;
        applyFlowControl(initialWindowSize, false);

        // Update the initial window size to allow another frame.
        int newInitialWindowSize = 2 * initialWindowSize;
        controller.initialInboundWindowSize(newInitialWindowSize);

        // Clear any previous calls to the writer.
        reset(frameWriter);

        // Send the next frame and verify that the expected window updates were sent.
        applyFlowControl(initialWindowSize, false);
        int delta = newInitialWindowSize - initialWindowSize;
        verify(frameWriter).writeFrame(eq(CONNECTION_STREAM_ID), eq(delta));
        verify(frameWriter).writeFrame(eq(STREAM_ID), eq(delta));
    }

    private static int getWindowDelta(int initialSize, int windowSize, int dataSize) {
        int newWindowSize = windowSize - dataSize;
        return initialSize - newWindowSize;
    }

    private void applyFlowControl(int dataSize, boolean endOfStream) throws Http2Exception {
        ByteBuf buf = dummyData(dataSize);
        controller.applyInboundFlowControl(STREAM_ID, buf, 0, endOfStream, frameWriter);
        buf.release();
    }

    private static ByteBuf dummyData(int size) {
        ByteBuf buffer = Unpooled.buffer(size);
        buffer.writerIndex(size);
        return buffer;
    }

    private void verifyWindowUpdateNotSent() throws Http2Exception {
        verify(frameWriter, never()).writeFrame(anyInt(), anyInt());
    }
}

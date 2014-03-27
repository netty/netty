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

import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.CONNECTION_STREAM_ID;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.connection.Http2Connection.Listener;
import io.netty.handler.codec.http2.draft10.connection.InboundFlowController.FrameWriter;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2WindowUpdateFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2WindowUpdateFrame;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DefaultInboundFlowController}.
 */
public class DefaultInboundFlowControllerTest {
    private static final int STREAM_ID = 1;

    private DefaultInboundFlowController controller;

    @Mock
    private Http2Connection connection;

    @Mock
    private ByteBuf buffer;

    @Mock
    private FrameWriter frameWriter;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Mock the creation of a single stream.
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Listener listener = (Listener) invocation.getArguments()[0];
                listener.streamCreated(STREAM_ID);
                return null;
            }
        }).when(connection).addListener(any(Listener.class));

        controller = new DefaultInboundFlowController(connection);
    }

    @Test
    public void dataFrameShouldBeAccepted() throws Http2Exception {
        Http2DataFrame frame = mockDataFrame(10, false);
        controller.applyInboundFlowControl(frame, frameWriter);
        verifyWindowUpdateNotSent();
    }

    @Test(expected = Http2Exception.class)
    public void connectionFlowControlExceededShouldThrow() throws Http2Exception {
        Http2DataFrame frame = mockDataFrame(DEFAULT_FLOW_CONTROL_WINDOW_SIZE + 1, true);
        controller.applyInboundFlowControl(frame, frameWriter);
    }

    @Test
    public void halfWindowRemainingShouldUpdateConnectionWindow() throws Http2Exception {
        int dataSize = (DEFAULT_FLOW_CONTROL_WINDOW_SIZE / 2) + 1;
        int newWindow = DEFAULT_FLOW_CONTROL_WINDOW_SIZE - dataSize;
        int windowDelta = DEFAULT_FLOW_CONTROL_WINDOW_SIZE - newWindow;

        // Set end-of-stream on the frame, so no window update will be sent for the stream.
        Http2DataFrame frame = mockDataFrame(dataSize, true);
        controller.applyInboundFlowControl(frame, frameWriter);
        verify(frameWriter).writeFrame(eq(windowUpdate(CONNECTION_STREAM_ID, windowDelta)));
    }

    @Test
    public void halfWindowRemainingShouldUpdateAllWindows() throws Http2Exception {
        int dataSize = (DEFAULT_FLOW_CONTROL_WINDOW_SIZE / 2) + 1;
        int initialWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
        int windowDelta = getWindowDelta(initialWindowSize, initialWindowSize, dataSize);

        // Don't set end-of-stream so we'll get a window update for the stream as well.
        Http2DataFrame frame = mockDataFrame(dataSize, false);
        controller.applyInboundFlowControl(frame, frameWriter);
        verify(frameWriter).writeFrame(eq(windowUpdate(CONNECTION_STREAM_ID, windowDelta)));
        verify(frameWriter).writeFrame(eq(windowUpdate(STREAM_ID, windowDelta)));
    }

    @Test
    public void initialWindowUpdateShouldAllowMoreFrames() throws Http2Exception {
        // Send a frame that takes up the entire window.
        int initialWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
        Http2DataFrame bigFrame = mockDataFrame(initialWindowSize, false);
        controller.applyInboundFlowControl(bigFrame, frameWriter);

        // Update the initial window size to allow another frame.
        int newInitialWindowSize = 2 * initialWindowSize;
        controller.setInitialInboundWindowSize(newInitialWindowSize);

        // Clear any previous calls to the writer.
        Mockito.reset(frameWriter);

        // Send the next frame and verify that the expected window updates were sent.
        controller.applyInboundFlowControl(bigFrame, frameWriter);
        int delta = newInitialWindowSize - initialWindowSize;
        verify(frameWriter).writeFrame(eq(windowUpdate(CONNECTION_STREAM_ID, delta)));
        verify(frameWriter).writeFrame(eq(windowUpdate(STREAM_ID, delta)));
    }

    private int getWindowDelta(int initialSize, int windowSize, int dataSize) {
        int newWindowSize = windowSize - dataSize;
        return initialSize - newWindowSize;
    }

    private Http2DataFrame mockDataFrame(int payloadLength, boolean endOfStream) {
        Http2DataFrame frame = Mockito.mock(Http2DataFrame.class);
        when(frame.getStreamId()).thenReturn(STREAM_ID);
        when(frame.isEndOfStream()).thenReturn(endOfStream);
        when(frame.content()).thenReturn(buffer);
        when(buffer.readableBytes()).thenReturn(payloadLength);
        return frame;
    }

    private void verifyWindowUpdateNotSent() {
        verify(frameWriter, never()).writeFrame(any(Http2WindowUpdateFrame.class));
    }

    private Http2WindowUpdateFrame windowUpdate(int streamId, int delta) {
        return new DefaultHttp2WindowUpdateFrame.Builder().setStreamId(streamId)
                .setWindowSizeIncrement(delta).build();
    }
}

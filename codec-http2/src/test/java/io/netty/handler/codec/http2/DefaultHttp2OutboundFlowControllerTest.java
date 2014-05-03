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
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.Http2OutboundFlowController.FrameWriter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DefaultHttp2OutboundFlowController}.
 */
public class DefaultHttp2OutboundFlowControllerTest {
    private static final int STREAM_A = 1;
    private static final int STREAM_B = 2;
    private static final int STREAM_C = 3;
    private static final int STREAM_D = 4;

    private DefaultHttp2OutboundFlowController controller;

    @Mock
    private ByteBuf buffer;

    @Mock
    private FrameWriter frameWriter;

    @Before
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        controller = new DefaultHttp2OutboundFlowController();
        controller.addStream(STREAM_A, 0, DEFAULT_PRIORITY_WEIGHT, false);
        controller.addStream(STREAM_B, 0, DEFAULT_PRIORITY_WEIGHT, false);
        controller.addStream(STREAM_C, STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
        controller.addStream(STREAM_D, STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
    }

    @Test
    public void frameShouldBeSentImmediately() throws Http2Exception {
        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyWrite(STREAM_A, data);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void stalledStreamShouldQueueFrame() throws Http2Exception {
        controller.initialOutboundWindowSize(0);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void nonZeroWindowShouldSendPartialFrame() throws Http2Exception {
        controller.initialOutboundWindowSize(5);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);

        // Verify that a partial frame of 5 was sent.
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(5, writtenBuf.readableBytes());
        assertEquals(data.slice(0, 5), writtenBuf);
        assertEquals(2, writtenBuf.refCnt());
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    @Test
    public void initialWindowUpdateShouldSendFrame() throws Http2Exception {
        controller.initialOutboundWindowSize(0);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that the entire frame was sent.
        controller.initialOutboundWindowSize(10);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(data, writtenBuf);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void initialWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        controller.initialOutboundWindowSize(0);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that a partial frame of 5 was sent.
        controller.initialOutboundWindowSize(5);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(5, writtenBuf.readableBytes());
        assertEquals(data.slice(0, 5), writtenBuf);
        assertEquals(2, writtenBuf.refCnt());
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    @Test
    public void connectionWindowUpdateShouldSendFrame() throws Http2Exception {
        // Set the connection window size to zero.
        controller
                .updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(data, writtenBuf);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void connectionWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        // Set the connection window size to zero.
        controller
                .updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that a partial frame of 5 was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 5);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(5, writtenBuf.readableBytes());
        assertEquals(data.slice(0, 5), writtenBuf);
        assertEquals(2, writtenBuf.refCnt());
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    @Test
    public void streamWindowUpdateShouldSendFrame() throws Http2Exception {
        // Set the stream window size to zero.
        controller.updateOutboundWindowSize(STREAM_A, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(STREAM_A, 10);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(data, writtenBuf);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void streamWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        // Set the stream window size to zero.
        controller.updateOutboundWindowSize(STREAM_A, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that a partial frame of 5 was sent.
        controller.updateOutboundWindowSize(STREAM_A, 5);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(5, writtenBuf.readableBytes());
        assertEquals(2, writtenBuf.refCnt());
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    /**
     * In this test, we block A which allows bytes to be written by C and D. Here's a view of the
     * tree (stream A is blocked).
     *
     * <pre>
     *         0
     *        / \
     *      [A]  B
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void blockedStreamShouldSpreadDataToChildren() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID,
                -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        // Block stream A
        controller.updateOutboundWindowSize(STREAM_A, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        // Try sending 10 bytes on each stream. They will be pending until we free up the
        // connection.
        send(STREAM_A, dummyData(10));
        send(STREAM_B, dummyData(10));
        send(STREAM_C, dummyData(10));
        send(STREAM_D, dummyData(10));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);

        // Verify that no write was done for A, since it's blocked.
        verifyNoWrite(STREAM_A);

        captureWrite(STREAM_B, captor, false);
        assertEquals(5, captor.getValue().readableBytes());

        // Verify that C and D each shared half of A's allowance. Since A's allowance (5) cannot
        // be split evenly, one will get 3 and one will get 2.
        captureWrite(STREAM_C, captor, false);
        int c = captor.getValue().readableBytes();
        captureWrite(STREAM_D, captor, false);
        int d = captor.getValue().readableBytes();
        assertEquals(5, c + d);
        assertEquals(1, Math.abs(c - d));
    }

    /**
     * In this test, we block B which allows all bytes to be written by A. A should not share the
     * data with its children since it's not blocked.
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void childrenShouldNotSendDataUntilParentBlocked() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID,
                -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        // Block stream B
        controller.updateOutboundWindowSize(STREAM_B, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        // Send 10 bytes to each.
        send(STREAM_A, dummyData(10));
        send(STREAM_B, dummyData(10));
        send(STREAM_C, dummyData(10));
        send(STREAM_D, dummyData(10));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);

        // Verify that A received all the bytes.
        captureWrite(STREAM_A, captor, false);
        assertEquals(10, captor.getValue().readableBytes());
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);
    }

    /**
     * In this test, we block B which allows all bytes to be written by A. Once A is blocked, it
     * will spill over the remaining of its portion to its children.
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void parentShouldWaterFallDataToChildren() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID,
                -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        // Block stream B
        controller.updateOutboundWindowSize(STREAM_B, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

        // Only send 5 to A so that it will allow data from its children.
        send(STREAM_A, dummyData(5));
        send(STREAM_B, dummyData(10));
        send(STREAM_C, dummyData(10));
        send(STREAM_D, dummyData(10));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);

        // Verify that no write was done for B, since it's blocked.
        verifyNoWrite(STREAM_B);

        captureWrite(STREAM_A, captor, false);
        assertEquals(5, captor.getValue().readableBytes());

        // Verify that C and D each shared half of A's allowance. Since A's allowance (5) cannot
        // be split evenly, one will get 3 and one will get 2.
        captureWrite(STREAM_C, captor, false);
        int c = captor.getValue().readableBytes();
        captureWrite(STREAM_D, captor, false);
        int d = captor.getValue().readableBytes();
        assertEquals(5, c + d);
        assertEquals(1, Math.abs(c - d));
    }

    private void send(int streamId, ByteBuf data) throws Http2Exception {
        controller.sendFlowControlled(streamId, data, 0, false, false, false, frameWriter);
    }

    private void verifyWrite(int streamId, ByteBuf data) {
        verify(frameWriter).writeFrame(eq(streamId), eq(data), eq(0), eq(false), eq(false),
                eq(false));
    }

    private void verifyNoWrite(int streamId) {
        verify(frameWriter, never()).writeFrame(eq(streamId), any(ByteBuf.class), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean());
    }

    private void captureWrite(int streamId, ArgumentCaptor<ByteBuf> captor, boolean endStream) {
        verify(frameWriter).writeFrame(eq(streamId), captor.capture(), eq(0), eq(endStream),
                eq(false), eq(false));
    }

    private ByteBuf dummyData(int size) {
        ByteBuf buffer = Unpooled.buffer(size);
        buffer.writerIndex(size);
        return buffer;
    }
}

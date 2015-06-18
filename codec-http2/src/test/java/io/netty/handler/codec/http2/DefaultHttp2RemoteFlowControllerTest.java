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
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_WINDOW_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2FrameWriter.Configuration;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DefaultHttp2RemoteFlowController}.
 */
public class DefaultHttp2RemoteFlowControllerTest {
    private static final int STREAM_A = 1;
    private static final int STREAM_B = 3;
    private static final int STREAM_C = 5;
    private static final int STREAM_D = 7;
    private static final int STREAM_E = 9;

    private DefaultHttp2RemoteFlowController controller;

    @Mock
    private ByteBuf buffer;

    @Mock
    private Http2FrameSizePolicy frameWriterSizePolicy;

    @Mock
    private Configuration frameWriterConfiguration;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ChannelPromise promise;

    @Mock
    private Http2RemoteFlowController.Listener listener;

    private DefaultHttp2Connection connection;

    @Before
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.flush()).thenThrow(new AssertionFailedError("forbidden"));

        connection = new DefaultHttp2Connection(false);
        controller = new DefaultHttp2RemoteFlowController(connection);
        controller.listener(listener);
        connection.remote().flowController(controller);

        connection.local().createStream(STREAM_A, false);
        connection.local().createStream(STREAM_B, false);
        Http2Stream streamC = connection.local().createStream(STREAM_C, false);
        Http2Stream streamD = connection.local().createStream(STREAM_D, false);
        streamC.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
    }

    @Test
    public void initialWindowSizeShouldOnlyChangeStreams() throws Http2Exception {
        controller.initialWindowSize(0);
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));
        assertEquals(0, window(STREAM_A));
        assertEquals(0, window(STREAM_B));
        assertEquals(0, window(STREAM_C));
        assertEquals(0, window(STREAM_D));
    }

    @Test
    public void windowUpdateShouldChangeConnectionWindow() throws Http2Exception {
        incrementWindowSize(CONNECTION_STREAM_ID, 100);
        assertEquals(DEFAULT_WINDOW_SIZE + 100, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_A));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_D));
    }

    @Test
    public void windowUpdateShouldChangeStreamWindow() throws Http2Exception {
        incrementWindowSize(STREAM_A, 100);
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE + 100, window(STREAM_A));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_D));
    }

    @Test
    public void payloadSmallerThanWindowShouldBeWrittenImmediately() throws Http2Exception {
        FakeFlowControlled data = new FakeFlowControlled(5);
        sendData(STREAM_A, data);
        data.assertNotWritten();
        verifyZeroInteractions(listener);
        controller.writePendingBytes();
        data.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 5);
    }

    @Test
    public void emptyPayloadShouldBeWrittenImmediately() throws Http2Exception {
        FakeFlowControlled data = new FakeFlowControlled(0);
        sendData(STREAM_A, data);
        data.assertNotWritten();
        controller.writePendingBytes();
        data.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 0);
    }

    @Test
    public void unflushedPayloadsShouldBeDroppedOnCancel() throws Http2Exception {
        FakeFlowControlled data = new FakeFlowControlled(5);
        sendData(STREAM_A, data);
        connection.stream(STREAM_A).close();
        controller.writePendingBytes();
        data.assertNotWritten();
        controller.writePendingBytes();
        data.assertNotWritten();
        verifyZeroInteractions(listener);
    }

    @Test
    public void payloadsShouldMerge() throws Http2Exception {
        controller.initialWindowSize(15);
        FakeFlowControlled data1 = new FakeFlowControlled(5, true);
        FakeFlowControlled data2 = new FakeFlowControlled(10, true);
        sendData(STREAM_A, data1);
        sendData(STREAM_A, data2);
        data1.assertNotWritten();
        data1.assertNotWritten();
        data2.assertMerged();
        controller.writePendingBytes();
        data1.assertFullyWritten();
        data2.assertNotWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 15);
    }

    @Test
    public void stalledStreamShouldQueuePayloads() throws Http2Exception {
        controller.initialWindowSize(0);

        FakeFlowControlled data = new FakeFlowControlled(15);
        FakeFlowControlled moreData = new FakeFlowControlled(0);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();
        sendData(STREAM_A, moreData);
        controller.writePendingBytes();
        moreData.assertNotWritten();
        verifyZeroInteractions(listener);
    }

    @Test
    public void queuedPayloadsReceiveErrorOnStreamClose() throws Http2Exception {
        controller.initialWindowSize(0);

        FakeFlowControlled data = new FakeFlowControlled(15);
        FakeFlowControlled moreData = new FakeFlowControlled(0);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();
        sendData(STREAM_A, moreData);
        controller.writePendingBytes();
        moreData.assertNotWritten();

        connection.stream(STREAM_A).close();
        data.assertError();
        moreData.assertError();
        verifyZeroInteractions(listener);
    }

    @Test
    public void payloadLargerThanWindowShouldWritePartial() throws Http2Exception {
        controller.initialWindowSize(5);

        final FakeFlowControlled data = new FakeFlowControlled(10);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        // Verify that a partial frame of 5 remains to be sent
        data.assertPartiallyWritten(5);
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 5);
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void windowUpdateAndFlushShouldTriggerWrite() throws Http2Exception {
        controller.initialWindowSize(10);

        FakeFlowControlled data = new FakeFlowControlled(20);
        FakeFlowControlled moreData = new FakeFlowControlled(10);
        sendData(STREAM_A, data);
        sendData(STREAM_A, moreData);
        controller.writePendingBytes();
        data.assertPartiallyWritten(10);
        moreData.assertNotWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 10);
        reset(ctx);

        // Update the window and verify that the rest of data and some of moreData are written
        incrementWindowSize(STREAM_A, 15);
        controller.writePendingBytes();

        data.assertFullyWritten();
        moreData.assertPartiallyWritten(5);
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 15);
        verifyNoMoreInteractions(listener);

        assertEquals(DEFAULT_WINDOW_SIZE - 25, window(CONNECTION_STREAM_ID));
        assertEquals(0, window(STREAM_A));
        assertEquals(10, window(STREAM_B));
        assertEquals(10, window(STREAM_C));
        assertEquals(10, window(STREAM_D));
    }

    @Test
    public void initialWindowUpdateShouldSendPayload() throws Http2Exception {
        controller.initialWindowSize(0);

        FakeFlowControlled data = new FakeFlowControlled(10);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();

        // Verify that the entire frame was sent.
        controller.initialWindowSize(10);
        data.assertFullyWritten();
    }

    @Test
    public void successiveSendsShouldNotInteract() throws Http2Exception {
        // Collapse the connection window to force queueing.
        incrementWindowSize(CONNECTION_STREAM_ID, -window(CONNECTION_STREAM_ID));
        assertEquals(0, window(CONNECTION_STREAM_ID));

        FakeFlowControlled dataA = new FakeFlowControlled(10);
        // Queue data for stream A and allow most of it to be written.
        sendData(STREAM_A, dataA);
        controller.writePendingBytes();
        dataA.assertNotWritten();
        incrementWindowSize(CONNECTION_STREAM_ID, 8);
        controller.writePendingBytes();
        dataA.assertPartiallyWritten(8);
        assertEquals(65527, window(STREAM_A));
        assertEquals(0, window(CONNECTION_STREAM_ID));
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 8);

        // Queue data for stream B and allow the rest of A and all of B to be written.
        FakeFlowControlled dataB = new FakeFlowControlled(10);
        sendData(STREAM_B, dataB);
        controller.writePendingBytes();
        dataB.assertNotWritten();
        incrementWindowSize(CONNECTION_STREAM_ID, 12);
        controller.writePendingBytes();
        assertEquals(0, window(CONNECTION_STREAM_ID));

        // Verify the rest of A is written.
        dataA.assertFullyWritten();
        assertEquals(65525, window(STREAM_A));
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 2);

        dataB.assertFullyWritten();
        assertEquals(65525, window(STREAM_B));
        verify(listener, times(1)).streamWritten(stream(STREAM_B), 10);
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void negativeWindowShouldNotThrowException() throws Http2Exception {
        final int initWindow = 20;
        final int secondWindowSize = 10;
        controller.initialWindowSize(initWindow);

        FakeFlowControlled data1 = new FakeFlowControlled(initWindow);
        FakeFlowControlled data2 = new FakeFlowControlled(5);

        // Deplete the stream A window to 0
        sendData(STREAM_A, data1);
        controller.writePendingBytes();
        data1.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 20);

        // Make the window size for stream A negative
        controller.initialWindowSize(initWindow - secondWindowSize);
        assertEquals(-secondWindowSize, window(STREAM_A));

        // Queue up a write. It should not be written now because the window is negative
        sendData(STREAM_A, data2);
        controller.writePendingBytes();
        data2.assertNotWritten();

        // Open the window size back up a bit (no send should happen)
        incrementWindowSize(STREAM_A, 5);
        controller.writePendingBytes();
        assertEquals(-5, window(STREAM_A));
        data2.assertNotWritten();

        // Open the window size back up a bit (no send should happen)
        incrementWindowSize(STREAM_A, 5);
        controller.writePendingBytes();
        assertEquals(0, window(STREAM_A));
        data2.assertNotWritten();

        // Open the window size back up and allow the write to happen
        incrementWindowSize(STREAM_A, 5);
        controller.writePendingBytes();
        data2.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 5);
    }

    @Test
    public void initialWindowUpdateShouldSendEmptyFrame() throws Http2Exception {
        controller.initialWindowSize(0);

        // First send a frame that will get buffered.
        FakeFlowControlled data = new FakeFlowControlled(10, false);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();

        // Now send an empty frame on the same stream and verify that it's also buffered.
        FakeFlowControlled data2 = new FakeFlowControlled(0, false);
        sendData(STREAM_A, data2);
        controller.writePendingBytes();
        data2.assertNotWritten();

        // Re-expand the window and verify that both frames were sent.
        controller.initialWindowSize(10);

        data.assertFullyWritten();
        data2.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 10);
    }

    @Test
    public void initialWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        controller.initialWindowSize(0);

        FakeFlowControlled data = new FakeFlowControlled(10);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();

        // Verify that a partial frame of 5 was sent.
        controller.initialWindowSize(5);
        data.assertPartiallyWritten(5);
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 5);
    }

    @Test
    public void connectionWindowUpdateShouldSendFrame() throws Http2Exception {
        // Set the connection window size to zero.
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        FakeFlowControlled data = new FakeFlowControlled(10);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();

        // Verify that the entire frame was sent.
        incrementWindowSize(CONNECTION_STREAM_ID, 10);
        data.assertNotWritten();
        controller.writePendingBytes();
        data.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 10);

        assertEquals(0, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE - 10, window(STREAM_A));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_D));
    }

    @Test
    public void connectionWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        // Set the connection window size to zero.
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        FakeFlowControlled data = new FakeFlowControlled(10);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();

        // Verify that a partial frame of 5 was sent.
        incrementWindowSize(CONNECTION_STREAM_ID, 5);
        data.assertNotWritten();
        controller.writePendingBytes();
        data.assertPartiallyWritten(5);
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 5);
        assertEquals(0, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE - 5, window(STREAM_A));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_D));
    }

    @Test
    public void streamWindowUpdateShouldSendFrame() throws Http2Exception {
        // Set the stream window size to zero.
        exhaustStreamWindow(STREAM_A);

        FakeFlowControlled data = new FakeFlowControlled(10);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();

        // Verify that the entire frame was sent.
        incrementWindowSize(STREAM_A, 10);
        data.assertNotWritten();
        controller.writePendingBytes();
        data.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 10);
        assertEquals(DEFAULT_WINDOW_SIZE - 10, window(CONNECTION_STREAM_ID));
        assertEquals(0, window(STREAM_A));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_D));
    }

    @Test
    public void streamWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        // Set the stream window size to zero.
        exhaustStreamWindow(STREAM_A);

        FakeFlowControlled data = new FakeFlowControlled(10);
        sendData(STREAM_A, data);
        controller.writePendingBytes();
        data.assertNotWritten();

        // Verify that a partial frame of 5 was sent.
        incrementWindowSize(STREAM_A, 5);
        data.assertNotWritten();
        controller.writePendingBytes();
        data.assertPartiallyWritten(5);
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 5);
        assertEquals(DEFAULT_WINDOW_SIZE - 5, window(CONNECTION_STREAM_ID));
        assertEquals(0, window(STREAM_A));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_D));
    }

    /**
     * In this test, we block A which allows bytes to be written by C and D. Here's a view of the tree (stream A is
     * blocked).
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
        // Block stream A
        exhaustStreamWindow(STREAM_A);

        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        // Try sending 10 bytes on each stream. They will be pending until we free up the
        // connection.

        FakeFlowControlled dataA = new FakeFlowControlled(10);
        FakeFlowControlled dataB = new FakeFlowControlled(10);
        FakeFlowControlled dataC = new FakeFlowControlled(10);
        FakeFlowControlled dataD = new FakeFlowControlled(10);

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();
        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        // Verify that the entire frame was sent.
        incrementWindowSize(CONNECTION_STREAM_ID, 10);
        controller.writePendingBytes();

        assertEquals(0, window(CONNECTION_STREAM_ID));

        // A is not written
        assertEquals(0, window(STREAM_A));
        dataA.assertNotWritten();

        // B is partially written
        assertEquals(DEFAULT_WINDOW_SIZE - 5, window(STREAM_B), 2);
        dataB.assertPartiallyWritten(5);
        verify(listener, times(1)).streamWritten(stream(STREAM_B), 5);

        // Verify that C and D each shared half of A's allowance. Since A's allowance (5) cannot
        // be split evenly, one will get 3 and one will get 2.
        assertEquals(2 * DEFAULT_WINDOW_SIZE - 5, window(STREAM_C) + window(STREAM_D), 5);
        dataC.assertPartiallyWritten(3);
        verify(listener, times(1)).streamWritten(stream(STREAM_C), 3);
        dataD.assertPartiallyWritten(2);
        verify(listener, times(1)).streamWritten(stream(STREAM_D), 2);
    }

    /**
     * In this test, we block B which allows all bytes to be written by A. A should not share the data with its children
     * since it's not blocked.
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
        // Block stream B
        exhaustStreamWindow(STREAM_B);

        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        FakeFlowControlled dataA = new FakeFlowControlled(10);
        FakeFlowControlled dataB = new FakeFlowControlled(10);
        FakeFlowControlled dataC = new FakeFlowControlled(10);
        FakeFlowControlled dataD = new FakeFlowControlled(10);

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        // Verify that the entire frame was sent.
        incrementWindowSize(CONNECTION_STREAM_ID, 10);
        controller.writePendingBytes();
        assertEquals(0, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE - 10, window(STREAM_A));
        assertEquals(0, window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_D));

        dataA.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 10);

        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();
    }

    /**
     * In this test, we block B which allows all bytes to be written by A. Once A is complete, it will spill over the
     * remaining of its portion to its children.
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
        // Block stream B
        exhaustStreamWindow(STREAM_B);

        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        // Only send 5 to A so that it will allow data from its children.
        FakeFlowControlled dataA = new FakeFlowControlled(5);
        FakeFlowControlled dataB = new FakeFlowControlled(10);
        FakeFlowControlled dataC = new FakeFlowControlled(10);
        FakeFlowControlled dataD = new FakeFlowControlled(10);

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        // Verify that the entire frame was sent.
        incrementWindowSize(CONNECTION_STREAM_ID, 10);
        controller.writePendingBytes();
        assertEquals(0, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE - 5, window(STREAM_A));
        assertEquals(0, window(STREAM_B));
        assertEquals(2 * DEFAULT_WINDOW_SIZE - 5, window(STREAM_C) + window(STREAM_D));

        // Verify that C and D each shared half of A's allowance. Since A's allowance (5) cannot
        // be split evenly, one will get 3 and one will get 2.
        dataA.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 5);
        dataB.assertNotWritten();
        dataC.assertPartiallyWritten(3);
        verify(listener, times(1)).streamWritten(stream(STREAM_C), 3);
        dataD.assertPartiallyWritten(2);
        verify(listener, times(1)).streamWritten(stream(STREAM_D), 2);
    }

    /**
     * In this test, we verify re-prioritizing a stream. We start out with B blocked:
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     *
     * We then re-prioritize D so that it's directly off of the connection and verify that A and D split the written
     * bytes between them.
     *
     * <pre>
     *           0
     *          /|\
     *        /  |  \
     *       A  [B]  D
     *      /
     *     C
     * </pre>
     */
    @Test
    public void reprioritizeShouldAdjustOutboundFlow() throws Http2Exception {
        // Block stream B
        exhaustStreamWindow(STREAM_B);

        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        // Send 10 bytes to each.
        FakeFlowControlled dataA = new FakeFlowControlled(10);
        FakeFlowControlled dataB = new FakeFlowControlled(10);
        FakeFlowControlled dataC = new FakeFlowControlled(10);
        FakeFlowControlled dataD = new FakeFlowControlled(10);

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        // Re-prioritize D as a direct child of the connection.
        setPriority(STREAM_D, 0, DEFAULT_PRIORITY_WEIGHT, false);

        // Verify that the entire frame was sent.
        incrementWindowSize(CONNECTION_STREAM_ID, 10);
        controller.writePendingBytes();
        assertEquals(0, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE - 5, window(STREAM_A), 2);
        assertEquals(0, window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE - 5, window(STREAM_D), 2);

        // Verify that A and D split the bytes.
        dataA.assertPartiallyWritten(5);
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 5);
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertPartiallyWritten(5);
        verify(listener, times(1)).streamWritten(stream(STREAM_D), 5);
    }

    /**
     * In this test, we root all streams at the connection, and then verify that data is split appropriately based on
     * weight (all available data is the same).
     *
     * <pre>
     *           0
     *        / / \ \
     *       A B   C D
     * </pre>
     */
    @Test
    public void writeShouldPreferHighestWeight() throws Http2Exception {
        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        // Root the streams at the connection and assign weights.
        setPriority(STREAM_A, 0, (short) 50, false);
        setPriority(STREAM_B, 0, (short) 200, false);
        setPriority(STREAM_C, 0, (short) 100, false);
        setPriority(STREAM_D, 0, (short) 100, false);

        FakeFlowControlled dataA = new FakeFlowControlled(1000);
        FakeFlowControlled dataB = new FakeFlowControlled(1000);
        FakeFlowControlled dataC = new FakeFlowControlled(1000);
        FakeFlowControlled dataD = new FakeFlowControlled(1000);

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        // Allow 1000 bytes to be sent.
        incrementWindowSize(CONNECTION_STREAM_ID, 1000);
        controller.writePendingBytes();

        // All writes sum == 1000
        assertEquals(1000, dataA.written() + dataB.written() + dataC.written() + dataD.written());
        int allowedError = 10;
        dataA.assertPartiallyWritten(109, allowedError);
        dataB.assertPartiallyWritten(445, allowedError);
        dataC.assertPartiallyWritten(223, allowedError);
        dataD.assertPartiallyWritten(223, allowedError);
        verify(listener, times(1)).streamWritten(eq(stream(STREAM_A)), anyInt());
        verify(listener, times(1)).streamWritten(eq(stream(STREAM_B)), anyInt());
        verify(listener, times(1)).streamWritten(eq(stream(STREAM_C)), anyInt());
        verify(listener, times(1)).streamWritten(eq(stream(STREAM_D)), anyInt());

        assertEquals(0, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE - dataA.written(), window(STREAM_A));
        assertEquals(DEFAULT_WINDOW_SIZE - dataB.written(), window(STREAM_B));
        assertEquals(DEFAULT_WINDOW_SIZE - dataC.written(), window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE - dataD.written(), window(STREAM_D));
    }

    /**
     * In this test, we root all streams at the connection, and then verify that data is split equally among the stream,
     * since they all have the same weight.
     *
     * <pre>
     *           0
     *        / / \ \
     *       A B   C D
     * </pre>
     */
    @Test
    public void samePriorityShouldDistributeBasedOnData() throws Http2Exception {
        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        // Root the streams at the connection with the same weights.
        setPriority(STREAM_A, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_B, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_C, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_D, 0, DEFAULT_PRIORITY_WEIGHT, false);

        // Send a bunch of data on each stream.
        FakeFlowControlled dataA = new FakeFlowControlled(400);
        FakeFlowControlled dataB = new FakeFlowControlled(500);
        FakeFlowControlled dataC = new FakeFlowControlled(0);
        FakeFlowControlled dataD = new FakeFlowControlled(700);

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        // The write will occur on C, because it's an empty frame.
        dataC.assertFullyWritten();
        verify(listener, times(1)).streamWritten(stream(STREAM_C), 0);
        dataD.assertNotWritten();

        // Allow 1000 bytes to be sent.
        incrementWindowSize(CONNECTION_STREAM_ID, 999);
        controller.writePendingBytes();

        assertEquals(0, window(CONNECTION_STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE - 333, window(STREAM_A), 50);
        assertEquals(DEFAULT_WINDOW_SIZE - 333, window(STREAM_B), 50);
        assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_C));
        assertEquals(DEFAULT_WINDOW_SIZE - 333, window(STREAM_D), 50);

        dataA.assertPartiallyWritten(333);
        verify(listener, times(1)).streamWritten(stream(STREAM_A), 333);
        dataB.assertPartiallyWritten(333);
        verify(listener, times(1)).streamWritten(stream(STREAM_B), 333);
        dataD.assertPartiallyWritten(333);
        verify(listener, times(1)).streamWritten(stream(STREAM_D), 333);
    }

    /**
     * In this test, we block all streams and verify the priority bytes for each sub tree at each node are correct
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrect() throws Http2Exception {
        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);

        FakeFlowControlled dataA = new FakeFlowControlled(streamSizes.get(STREAM_A));
        FakeFlowControlled dataB = new FakeFlowControlled(streamSizes.get(STREAM_B));
        FakeFlowControlled dataC = new FakeFlowControlled(streamSizes.get(STREAM_C));
        FakeFlowControlled dataD = new FakeFlowControlled(streamSizes.get(STREAM_D));

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)),
                        streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_A, STREAM_C, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_B)),
                streamableBytesForTree(streamB));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_C)),
                streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_D)),
                streamableBytesForTree(streamD));
    }

    /**
     * In this test, we block all streams shift the priority tree and verify priority bytes for each subtree are correct
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     *
     * <pre>
     *        [0]
     *         |
     *         A
     *         |
     *         B
     *        / \
     *       C   D
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrectWithRestructure() throws Http2Exception {
        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);

        FakeFlowControlled dataA = new FakeFlowControlled(streamSizes.get(STREAM_A));
        FakeFlowControlled dataB = new FakeFlowControlled(streamSizes.get(STREAM_B));
        FakeFlowControlled dataC = new FakeFlowControlled(streamSizes.get(STREAM_C));
        FakeFlowControlled dataD = new FakeFlowControlled(streamSizes.get(STREAM_D));

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        streamB.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)),
                        streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)),
                        streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_B, STREAM_C, STREAM_D)),
                     streamableBytesForTree(streamB));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_C)),
                streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_D)),
                streamableBytesForTree(streamD));
    }

    /**
     * In this test, we block all streams and add a node to the priority tree and verify
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *       |
     *       E
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrectWithAddition() throws Http2Exception {
        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        Http2Stream streamE = connection.local().createStream(STREAM_E, false);
        streamE.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);
        streamSizes.put(STREAM_E, 900);

        FakeFlowControlled dataA = new FakeFlowControlled(streamSizes.get(STREAM_A));
        FakeFlowControlled dataB = new FakeFlowControlled(streamSizes.get(STREAM_B));
        FakeFlowControlled dataC = new FakeFlowControlled(streamSizes.get(STREAM_C));
        FakeFlowControlled dataD = new FakeFlowControlled(streamSizes.get(STREAM_D));
        FakeFlowControlled dataE = new FakeFlowControlled(streamSizes.get(STREAM_E));

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        sendData(STREAM_E, dataE);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();
        dataE.assertNotWritten();

        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D, STREAM_E)),
                streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_E, STREAM_C, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_B)),
                streamableBytesForTree(streamB));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_C)),
                streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_D)),
                streamableBytesForTree(streamD));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_E, STREAM_C, STREAM_D)),
                streamableBytesForTree(streamE));
    }

    /**
     * In this test, we block all streams and close an internal stream in the priority tree but tree should not change
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrectWithInternalStreamClose() throws Http2Exception {
        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);

        FakeFlowControlled dataA = new FakeFlowControlled(streamSizes.get(STREAM_A));
        FakeFlowControlled dataB = new FakeFlowControlled(streamSizes.get(STREAM_B));
        FakeFlowControlled dataC = new FakeFlowControlled(streamSizes.get(STREAM_C));
        FakeFlowControlled dataD = new FakeFlowControlled(streamSizes.get(STREAM_D));

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        streamA.close();

        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_B, STREAM_C, STREAM_D)),
                streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_C, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_B)),
                streamableBytesForTree(streamB));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_C)),
                streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_D)),
                streamableBytesForTree(streamD));
    }

    /**
     * In this test, we block all streams and close a leaf stream in the priority tree and verify
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the close:
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *       |
     *       D
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrectWithLeafStreamClose() throws Http2Exception {
        // Block the connection
        exhaustStreamWindow(CONNECTION_STREAM_ID);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);

        FakeFlowControlled dataA = new FakeFlowControlled(streamSizes.get(STREAM_A));
        FakeFlowControlled dataB = new FakeFlowControlled(streamSizes.get(STREAM_B));
        FakeFlowControlled dataC = new FakeFlowControlled(streamSizes.get(STREAM_C));
        FakeFlowControlled dataD = new FakeFlowControlled(streamSizes.get(STREAM_D));

        sendData(STREAM_A, dataA);
        sendData(STREAM_B, dataB);
        sendData(STREAM_C, dataC);
        sendData(STREAM_D, dataD);
        controller.writePendingBytes();

        dataA.assertNotWritten();
        dataB.assertNotWritten();
        dataC.assertNotWritten();
        dataD.assertNotWritten();

        streamC.close();

        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_A, STREAM_B, STREAM_D)),
                streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_A, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_B)),
                streamableBytesForTree(streamB));
        assertEquals(0, streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_D)),
                streamableBytesForTree(streamD));
    }

    @Test
    public void flowControlledWriteThrowsAnException() throws Exception {
        final Http2RemoteFlowController.FlowControlled flowControlled = mockedFlowControlledThatThrowsOnWrite();
        final Http2Stream stream = stream(STREAM_A);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                stream.closeLocalSide();
                return null;
            }
        }).when(flowControlled).error(any(Throwable.class));

        int windowBefore = window(STREAM_A);

        controller.addFlowControlled(ctx, stream, flowControlled);
        controller.writePendingBytes();

        verify(flowControlled, times(3)).write(anyInt());
        verify(flowControlled).error(any(Throwable.class));
        verify(flowControlled, never()).writeComplete();

        assertEquals(90, windowBefore - window(STREAM_A));
    }

    @Test
    public void flowControlledWriteAndErrorThrowAnException() throws Exception {
        final Http2RemoteFlowController.FlowControlled flowControlled = mockedFlowControlledThatThrowsOnWrite();
        final Http2Stream stream = stream(STREAM_A);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                throw new RuntimeException("error failed");
            }
        }).when(flowControlled).error(any(Throwable.class));

        int windowBefore = window(STREAM_A);

        boolean exceptionThrown = false;
        try {
            controller.addFlowControlled(ctx, stream, flowControlled);
            controller.writePendingBytes();
        } catch (RuntimeException e) {
            exceptionThrown = true;
        } finally {
            assertTrue(exceptionThrown);
        }

        verify(flowControlled, times(3)).write(anyInt());
        verify(flowControlled).error(any(Throwable.class));
        verify(flowControlled, never()).writeComplete();

        assertEquals(90, windowBefore - window(STREAM_A));
    }

    @Test
    public void flowControlledWriteCompleteThrowsAnException() throws Exception {
        final Http2RemoteFlowController.FlowControlled flowControlled =
                Mockito.mock(Http2RemoteFlowController.FlowControlled.class);
        final AtomicInteger size = new AtomicInteger(150);
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                return size.get();
            }
        }).when(flowControlled).size();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                size.addAndGet(-50);
                return null;
            }
        }).when(flowControlled).write(anyInt());

        final Http2Stream stream = stream(STREAM_A);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                throw new RuntimeException("writeComplete failed");
            }
        }).when(flowControlled).writeComplete();

        int windowBefore = window(STREAM_A);

        try {
            controller.addFlowControlled(ctx, stream, flowControlled);
            controller.writePendingBytes();
        } catch (Exception e) {
            fail();
        }

        verify(flowControlled, times(3)).write(anyInt());
        verify(flowControlled, never()).error(any(Throwable.class));
        verify(flowControlled).writeComplete();

        assertEquals(150, windowBefore - window(STREAM_A));
    }

    @Test
    public void closeStreamInFlowControlledError() throws Exception {
        final Http2RemoteFlowController.FlowControlled flowControlled =
                Mockito.mock(Http2RemoteFlowController.FlowControlled.class);
        final Http2Stream stream = stream(STREAM_A);
        when(flowControlled.size()).thenReturn(100);
        doThrow(new RuntimeException("write failed")).when(flowControlled).write(anyInt());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                stream.close();
                return null;
            }
        }).when(flowControlled).error(any(Throwable.class));

        controller.addFlowControlled(ctx, stream, flowControlled);
        controller.writePendingBytes();

        verify(flowControlled).write(anyInt());
        verify(flowControlled).error(any(Throwable.class));
        verify(flowControlled, never()).writeComplete();
    }

    private static Http2RemoteFlowController.FlowControlled mockedFlowControlledThatThrowsOnWrite() throws Exception {
        final Http2RemoteFlowController.FlowControlled flowControlled =
                Mockito.mock(Http2RemoteFlowController.FlowControlled.class);
        when(flowControlled.size()).thenReturn(100);
        doAnswer(new Answer<Void>() {
            private int invocationCount;
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                switch(invocationCount) {
                case 0:
                    when(flowControlled.size()).thenReturn(50);
                    invocationCount = 1;
                    return null;
                case 1:
                    when(flowControlled.size()).thenReturn(20);
                    invocationCount = 2;
                    return null;
                default:
                    when(flowControlled.size()).thenReturn(10);
                    throw new RuntimeException("Write failed");
                }
            }
        }).when(flowControlled).write(anyInt());
        return flowControlled;
    }

    private static int calculateStreamSizeSum(IntObjectMap<Integer> streamSizes, List<Integer> streamIds) {
        int sum = 0;
        for (Integer streamId : streamIds) {
            Integer streamSize = streamSizes.get(streamId);
            if (streamSize != null) {
                sum += streamSize;
            }
        }
        return sum;
    }

    private void sendData(int streamId, FakeFlowControlled data) throws Http2Exception {
        Http2Stream stream = stream(streamId);
        controller.addFlowControlled(ctx, stream, data);
    }

    private void setPriority(int stream, int parent, int weight, boolean exclusive) throws Http2Exception {
        connection.stream(stream).setPriority(parent, (short) weight, exclusive);
    }

    private void exhaustStreamWindow(int streamId) throws Http2Exception {
        incrementWindowSize(streamId, -window(streamId));
    }

    private int window(int streamId) throws Http2Exception {
        return controller.windowSize(stream(streamId));
    }

    private void incrementWindowSize(int streamId, int delta) throws Http2Exception {
        controller.incrementWindowSize(ctx, stream(streamId), delta);
    }

    private int streamableBytesForTree(Http2Stream stream) {
        return controller.streamableBytesForTree(stream);
    }

    private Http2Stream stream(int streamId) {
        return connection.stream(streamId);
    }

    private static final class FakeFlowControlled implements Http2RemoteFlowController.FlowControlled {

        private int currentSize;
        private int originalSize;
        private boolean writeCalled;
        private final boolean mergeable;
        private boolean merged;

        private Throwable t;

        private FakeFlowControlled(int size) {
            this.currentSize = size;
            this.originalSize = size;
            this.mergeable = false;
        }

        private FakeFlowControlled(int size, boolean mergeable) {
            this.currentSize = size;
            this.originalSize = size;
            this.mergeable = mergeable;
        }

        @Override
        public int size() {
            return currentSize;
        }

        @Override
        public void error(Throwable t) {
            this.t = t;
        }

        @Override
        public void writeComplete() {
        }

        @Override
        public void write(int allowedBytes) {
            if (allowedBytes <= 0 && currentSize != 0) {
                // Write has been called but no data can be written
                return;
            }
            writeCalled = true;
            int written = Math.min(currentSize, allowedBytes);
            currentSize -= written;
        }

        @Override
        public boolean merge(Http2RemoteFlowController.FlowControlled next) {
            if (mergeable && next instanceof FakeFlowControlled) {
                this.originalSize += ((FakeFlowControlled) next).originalSize;
                this.currentSize += ((FakeFlowControlled) next).originalSize;
                ((FakeFlowControlled) next).merged = true;
                return true;
            }
            return false;
        }

        public int written() {
            return originalSize - currentSize;
        }

        public void assertNotWritten() {
            assertFalse(writeCalled);
        }

        public void assertPartiallyWritten(int expectedWritten) {
            assertPartiallyWritten(expectedWritten, 0);
        }

        public void assertPartiallyWritten(int expectedWritten, int delta) {
            assertTrue(writeCalled);
            assertEquals(expectedWritten, written(), delta);
        }

        public void assertFullyWritten() {
            assertTrue(writeCalled);
            assertEquals(0, currentSize);
        }

        public boolean assertMerged() {
            return merged;
        }

        public void assertError() {
            assertNotNull(t);
        }
    }
}

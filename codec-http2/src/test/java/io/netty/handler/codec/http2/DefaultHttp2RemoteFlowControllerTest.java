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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2FrameWriter.Configuration;
import io.netty.util.concurrent.EventExecutor;
import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_WINDOW_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultHttp2RemoteFlowController}.
 */
public class DefaultHttp2RemoteFlowControllerTest {
    private static final int STREAM_A = 1;
    private static final int STREAM_B = 3;
    private static final int STREAM_C = 5;
    private static final int STREAM_D = 7;

    private DefaultHttp2RemoteFlowController controller;
    private PriorityStreamByteDistributor distributor;

    @Mock
    private ByteBuf buffer;

    @Mock
    private Http2FrameSizePolicy frameWriterSizePolicy;

    @Mock
    private Configuration frameWriterConfiguration;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private ChannelConfig config;

    @Mock
    private EventExecutor executor;

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
        setChannelWritability(true);
        when(channel.config()).thenReturn(config);
        when(executor.inEventLoop()).thenReturn(true);

        initConnectionAndController();

        resetCtx();
        // This is intentionally left out of initConnectionAndController so it can be tested below.
        controller.channelHandlerContext(ctx);
    }

    private void initConnectionAndController() throws Http2Exception {
        connection = new DefaultHttp2Connection(false);
        distributor = new PriorityStreamByteDistributor(connection);
        controller = new DefaultHttp2RemoteFlowController(connection, distributor);
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
        resetCtx();

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
        }).when(flowControlled).error(any(ChannelHandlerContext.class), any(Throwable.class));

        int windowBefore = window(STREAM_A);

        controller.addFlowControlled(stream, flowControlled);
        controller.writePendingBytes();

        verify(flowControlled, times(3)).write(any(ChannelHandlerContext.class), anyInt());
        verify(flowControlled).error(any(ChannelHandlerContext.class), any(Throwable.class));
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
                throw new RuntimeException("Fake exception");
            }
        }).when(flowControlled).error(any(ChannelHandlerContext.class), any(Throwable.class));

        int windowBefore = window(STREAM_A);

        boolean exceptionThrown = false;
        try {
            controller.addFlowControlled(stream, flowControlled);
            controller.writePendingBytes();
        } catch (Http2Exception e) {
            exceptionThrown = true;
        } finally {
            assertTrue(exceptionThrown);
        }

        verify(flowControlled, times(3)).write(any(ChannelHandlerContext.class), anyInt());
        verify(flowControlled).error(any(ChannelHandlerContext.class), any(Throwable.class));
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
        }).when(flowControlled).write(any(ChannelHandlerContext.class), anyInt());

        final Http2Stream stream = stream(STREAM_A);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                throw new RuntimeException("writeComplete failed");
            }
        }).when(flowControlled).writeComplete();

        int windowBefore = window(STREAM_A);

        try {
            controller.addFlowControlled(stream, flowControlled);
            controller.writePendingBytes();
        } catch (Exception e) {
            fail();
        }

        verify(flowControlled, times(3)).write(any(ChannelHandlerContext.class), anyInt());
        verify(flowControlled, never()).error(any(ChannelHandlerContext.class), any(Throwable.class));
        verify(flowControlled).writeComplete();

        assertEquals(150, windowBefore - window(STREAM_A));
    }

    @Test
    public void closeStreamInFlowControlledError() throws Exception {
        final Http2RemoteFlowController.FlowControlled flowControlled =
                Mockito.mock(Http2RemoteFlowController.FlowControlled.class);
        final Http2Stream stream = stream(STREAM_A);
        when(flowControlled.size()).thenReturn(100);
        doThrow(new RuntimeException("write failed"))
            .when(flowControlled).write(any(ChannelHandlerContext.class), anyInt());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                stream.close();
                return null;
            }
        }).when(flowControlled).error(any(ChannelHandlerContext.class), any(Throwable.class));

        controller.addFlowControlled(stream, flowControlled);
        controller.writePendingBytes();

        verify(flowControlled).write(any(ChannelHandlerContext.class), anyInt());
        verify(flowControlled).error(any(ChannelHandlerContext.class), any(Throwable.class));
        verify(flowControlled, never()).writeComplete();
    }

    @Test
    public void nonWritableChannelDoesNotAttemptToWrite() throws Exception {
        // Start the channel as not writable and exercise the public methods of the flow controller
        // making sure no frames are written.
        setChannelWritability(false);
        FakeFlowControlled dataA = new FakeFlowControlled(1);
        FakeFlowControlled dataB = new FakeFlowControlled(1);
        final Http2Stream stream = stream(STREAM_A);

        controller.addFlowControlled(stream, dataA);
        controller.writePendingBytes();
        dataA.assertNotWritten();

        controller.incrementWindowSize(stream, 100);
        controller.writePendingBytes();
        dataA.assertNotWritten();

        controller.addFlowControlled(stream, dataB);
        controller.writePendingBytes();
        dataA.assertNotWritten();
        dataB.assertNotWritten();

        // Now change the channel to writable and make sure frames are written.
        setChannelWritability(true);
        controller.writePendingBytes();
        dataA.assertFullyWritten();
        dataB.assertFullyWritten();
    }

    @Test
    public void contextShouldSendQueuedFramesWhenSet() throws Exception {
        // Re-initialize the controller so we can ensure the context hasn't been set yet.
        initConnectionAndController();

        FakeFlowControlled dataA = new FakeFlowControlled(1);
        final Http2Stream stream = stream(STREAM_A);

        // Queue some frames
        controller.addFlowControlled(stream, dataA);
        controller.writePendingBytes();
        dataA.assertNotWritten();

        controller.incrementWindowSize(stream, 100);
        controller.writePendingBytes();
        dataA.assertNotWritten();

        // Set the controller
        controller.channelHandlerContext(ctx);
        dataA.assertFullyWritten();
    }

    @Test
    public void reentryDeallocatesAllBytesAndPropegatesHttp2Exception() throws Http2Exception {
        final int dataSize = 1;
        final AtomicInteger exceptionsSeen = new AtomicInteger();
        final AtomicInteger callCount = new AtomicInteger(2);
        final RuntimeException fakeException = new RuntimeException("Fake exception");
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                if (callCount.decrementAndGet() == 0) {
                    throw fakeException;
                } else {
                    try {
                        FakeFlowControlled dataA = new FakeFlowControlled(dataSize);
                        final Http2Stream stream = stream(STREAM_A);
                        controller.addFlowControlled(stream, dataA);
                        controller.writePendingBytes();
                    } catch (Http2Exception e) {
                        assertSame(fakeException, e.getCause());
                        exceptionsSeen.incrementAndGet();
                    }
                }
                return null;
            }
        }).when(listener).streamWritten(any(Http2Stream.class), anyInt());

        FakeFlowControlled dataA = new FakeFlowControlled(dataSize);
        final Http2Stream stream = stream(STREAM_A);
        controller.addFlowControlled(stream, dataA);
        controller.writePendingBytes();
        assertEquals(1, exceptionsSeen.get());
        assertEquals(0, distributor.unallocatedStreamableBytesForTree(stream(CONNECTION_STREAM_ID)));
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
        }).when(flowControlled).write(any(ChannelHandlerContext.class), anyInt());
        return flowControlled;
    }

    private void sendData(int streamId, FakeFlowControlled data) throws Http2Exception {
        Http2Stream stream = stream(streamId);
        controller.addFlowControlled(stream, data);
    }

    private void exhaustStreamWindow(int streamId) throws Http2Exception {
        incrementWindowSize(streamId, -window(streamId));
    }

    private int window(int streamId) throws Http2Exception {
        return controller.windowSize(stream(streamId));
    }

    private void incrementWindowSize(int streamId, int delta) throws Http2Exception {
        controller.incrementWindowSize(stream(streamId), delta);
    }

    private Http2Stream stream(int streamId) {
        return connection.stream(streamId);
    }

    private void resetCtx() {
        reset(ctx);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.executor()).thenReturn(executor);
    }

    private void setChannelWritability(boolean isWritable) {
        when(channel.bytesBeforeUnwritable()).thenReturn(isWritable ? Long.MAX_VALUE : 0);
        when(channel.isWritable()).thenReturn(isWritable);
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
        public void error(ChannelHandlerContext ctx, Throwable t) {
            this.t = t;
        }

        @Override
        public void writeComplete() {
        }

        @Override
        public void write(ChannelHandlerContext ctx, int allowedBytes) {
            if (allowedBytes <= 0 && currentSize != 0) {
                // Write has been called but no data can be written
                return;
            }
            writeCalled = true;
            int written = Math.min(currentSize, allowedBytes);
            currentSize -= written;
        }

        @Override
        public boolean merge(ChannelHandlerContext ctx, Http2RemoteFlowController.FlowControlled next) {
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

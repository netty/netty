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

package io.netty.handler.codec.http2.draft10.connection;

import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.CONNECTION_STREAM_ID;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.connection.Http2Connection.Listener;
import io.netty.handler.codec.http2.draft10.connection.OutboundFlowController.FrameWriter;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link DefaultOutboundFlowController}.
 */
public class DefaultOutboundFlowControllerTest {
  private static final int STREAM_ID = 1;

  private DefaultOutboundFlowController controller;

  @Mock
  private Http2Connection connection;

  @Mock
  private ByteBuf buffer;

  @Mock
  private FrameWriter frameWriter;

  @Mock
  private Http2Stream stream;

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
    when(connection.getActiveStreams()).thenReturn(ImmutableList.of(stream));
    when(stream.getId()).thenReturn(STREAM_ID);

    controller = new DefaultOutboundFlowController(connection);
  }

  @Test
  public void frameShouldBeSentImmediately() throws Http2Exception {
    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);
    verify(frameWriter).writeFrame(frame);
    assertEquals(1, frame.refCnt());
    frame.release();
  }

  @Test
  public void stalledStreamShouldQueueFrame() throws Http2Exception {
    controller.setInitialOutboundWindowSize(0);

    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);
    verify(frameWriter, never()).writeFrame(frame);
    assertEquals(1, frame.refCnt());
    frame.release();
  }

  @Test
  public void nonZeroWindowShouldSendPartialFrame() throws Http2Exception {
    controller.setInitialOutboundWindowSize(5);

    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);

    // Verify that a partial frame of 5 was sent.
    ArgumentCaptor<Http2DataFrame> argument = ArgumentCaptor.forClass(Http2DataFrame.class);
    verify(frameWriter).writeFrame(argument.capture());
    Http2DataFrame writtenFrame = argument.getValue();
    assertEquals(STREAM_ID, writtenFrame.getStreamId());
    assertEquals(5, writtenFrame.content().readableBytes());
    assertEquals(2, writtenFrame.refCnt());
    assertEquals(2, frame.refCnt());
    frame.release(2);
  }

  @Test
  public void initialWindowUpdateShouldSendFrame() throws Http2Exception {
    controller.setInitialOutboundWindowSize(0);

    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);
    verify(frameWriter, never()).writeFrame(frame);

    // Verify that the entire frame was sent.
    controller.setInitialOutboundWindowSize(10);
    ArgumentCaptor<Http2DataFrame> argument = ArgumentCaptor.forClass(Http2DataFrame.class);
    verify(frameWriter).writeFrame(argument.capture());
    Http2DataFrame writtenFrame = argument.getValue();
    assertEquals(frame, writtenFrame);
    assertEquals(1, frame.refCnt());
    frame.release();
  }

  @Test
  public void initialWindowUpdateShouldSendPartialFrame() throws Http2Exception {
    controller.setInitialOutboundWindowSize(0);

    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);
    verify(frameWriter, never()).writeFrame(frame);

    // Verify that a partial frame of 5 was sent.
    controller.setInitialOutboundWindowSize(5);
    ArgumentCaptor<Http2DataFrame> argument = ArgumentCaptor.forClass(Http2DataFrame.class);
    verify(frameWriter).writeFrame(argument.capture());
    Http2DataFrame writtenFrame = argument.getValue();
    assertEquals(STREAM_ID, writtenFrame.getStreamId());
    assertEquals(5, writtenFrame.content().readableBytes());
    assertEquals(2, writtenFrame.refCnt());
    assertEquals(2, frame.refCnt());
    frame.release(2);
  }

  @Test
  public void connectionWindowUpdateShouldSendFrame() throws Http2Exception {
    // Set the connection window size to zero.
    controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);
    verify(frameWriter, never()).writeFrame(frame);

    // Verify that the entire frame was sent.
    controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
    ArgumentCaptor<Http2DataFrame> argument = ArgumentCaptor.forClass(Http2DataFrame.class);
    verify(frameWriter).writeFrame(argument.capture());
    Http2DataFrame writtenFrame = argument.getValue();
    assertEquals(frame, writtenFrame);
    assertEquals(1, frame.refCnt());
    frame.release();
  }

  @Test
  public void connectionWindowUpdateShouldSendPartialFrame() throws Http2Exception {
    // Set the connection window size to zero.
    controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);
    verify(frameWriter, never()).writeFrame(frame);

    // Verify that a partial frame of 5 was sent.
    controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 5);
    ArgumentCaptor<Http2DataFrame> argument = ArgumentCaptor.forClass(Http2DataFrame.class);
    verify(frameWriter).writeFrame(argument.capture());
    Http2DataFrame writtenFrame = argument.getValue();
    assertEquals(STREAM_ID, writtenFrame.getStreamId());
    assertEquals(5, writtenFrame.content().readableBytes());
    assertEquals(2, writtenFrame.refCnt());
    assertEquals(2, frame.refCnt());
    frame.release(2);
  }

  @Test
  public void streamWindowUpdateShouldSendFrame() throws Http2Exception {
    // Set the stream window size to zero.
    controller.updateOutboundWindowSize(STREAM_ID, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);
    verify(frameWriter, never()).writeFrame(frame);

    // Verify that the entire frame was sent.
    controller.updateOutboundWindowSize(STREAM_ID, 10);
    ArgumentCaptor<Http2DataFrame> argument = ArgumentCaptor.forClass(Http2DataFrame.class);
    verify(frameWriter).writeFrame(argument.capture());
    Http2DataFrame writtenFrame = argument.getValue();
    assertEquals(frame, writtenFrame);
    assertEquals(1, frame.refCnt());
    frame.release();
  }

  @Test
  public void streamWindowUpdateShouldSendPartialFrame() throws Http2Exception {
    // Set the stream window size to zero.
    controller.updateOutboundWindowSize(STREAM_ID, -DEFAULT_FLOW_CONTROL_WINDOW_SIZE);

    Http2DataFrame frame = frame(10);
    controller.sendFlowControlled(frame, frameWriter);
    verify(frameWriter, never()).writeFrame(frame);

    // Verify that a partial frame of 5 was sent.
    controller.updateOutboundWindowSize(STREAM_ID, 5);
    ArgumentCaptor<Http2DataFrame> argument = ArgumentCaptor.forClass(Http2DataFrame.class);
    verify(frameWriter).writeFrame(argument.capture());
    Http2DataFrame writtenFrame = argument.getValue();
    assertEquals(STREAM_ID, writtenFrame.getStreamId());
    assertEquals(5, writtenFrame.content().readableBytes());
    assertEquals(2, writtenFrame.refCnt());
    assertEquals(2, frame.refCnt());
    frame.release(2);
  }

  private static Http2DataFrame frame(int payloadLength) {
    ByteBuf buffer = Unpooled.buffer(payloadLength);
    buffer.writerIndex(payloadLength);
    return new DefaultHttp2DataFrame.Builder().setStreamId(STREAM_ID).setContent(buffer).build();
  }
}

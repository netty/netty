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

import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;

/**
 * Controls the outbound flow of data frames to the remote endpoint.
 */
public interface OutboundFlowController {

  /**
   * Interface that abstracts the writing of {@link Http2Frame} objects to the remote endpoint.
   */
  interface FrameWriter {

    /**
     * Writes a single data frame to the remote endpoint.
     */
    void writeFrame(Http2DataFrame frame);

    /**
     * Called if an error occurred before the write could take place. Sets the failure on the
     * channel promise.
     */
    void setFailure(Throwable cause);
  }

  /**
   * Sets the initial size of the connection's outbound flow control window. The outbound flow
   * control windows for all streams are updated by the delta in the initial window size. This is
   * called as part of the processing of a SETTINGS frame received from the remote endpoint.
   *
   * @param newWindowSize the new initial window size.
   */
  void setInitialOutboundWindowSize(int newWindowSize) throws Http2Exception;

  /**
   * Updates the size of the stream's outbound flow control window. This is called upon receiving a
   * WINDOW_UPDATE frame from the remote endpoint.
   *
   * @param streamId the ID of the stream, or zero if the window is for the entire connection.
   * @param deltaWindowSize the change in size of the outbound flow control window.
   * @throws Http2Exception thrown if a protocol-related error occurred.
   */
  void updateOutboundWindowSize(int streamId, int deltaWindowSize) throws Http2Exception;

  /**
   * Sends the frame with outbound flow control applied. The frame may be written at a later time,
   * depending on whether the remote endpoint can receive the frame now.
   * <p>
   * Data frame flow control processing requirements:
   * <p>
   * Sender must not send a data frame with data length greater than the transfer window size. After
   * sending each data frame, the stream's transfer window size is decremented by the amount of data
   * transmitted. When the window size becomes less than or equal to 0, the sender must pause
   * transmitting data frames.
   *
   * @param frame the frame to send.
   * @param frameWriter peforms to the write of the frame to the remote endpoint.
   * @throws Http2Exception thrown if a protocol-related error occurred.
   */
  void sendFlowControlled(Http2DataFrame frame, FrameWriter frameWriter) throws Http2Exception;
}

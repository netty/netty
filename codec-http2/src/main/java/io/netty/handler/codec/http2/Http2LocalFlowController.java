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
import io.netty.util.internal.UnstableApi;

/**
 * A {@link Http2FlowController} for controlling the inbound flow of {@code DATA} frames from the remote endpoint.
 */
@UnstableApi
public interface Http2LocalFlowController extends Http2FlowController {
    /**
     * Sets the writer to be use for sending {@code WINDOW_UPDATE} frames. This must be called before any flow
     * controlled data is received.
     *
     * @param frameWriter the HTTP/2 frame writer.
     */
    Http2LocalFlowController frameWriter(Http2FrameWriter frameWriter);

    /**
     * Receives an inbound {@code DATA} frame from the remote endpoint and applies flow control policies to it for both
     * the {@code stream} as well as the connection. If any flow control policies have been violated, an exception is
     * raised immediately, otherwise the frame is considered to have "passed" flow control.
     * <p/>
     * If {@code stream} is {@code null} or closed, flow control should only be applied to the connection window and the
     * bytes are immediately consumed.
     *
     * @param stream the subject stream for the received frame. The connection stream object must not be used. If {@code
     * stream} is {@code null} or closed, flow control should only be applied to the connection window and the bytes are
     * immediately consumed.
     * @param data payload buffer for the frame.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote endpoint for this stream.
     * @throws Http2Exception if any flow control errors are encountered.
     */
    void receiveFlowControlledFrame(Http2Stream stream, ByteBuf data, int padding,
                                    boolean endOfStream) throws Http2Exception;

    /**
     * Indicates that the application has consumed a number of bytes for the given stream and is therefore ready to
     * receive more data from the remote endpoint. The application must consume any bytes that it receives or the flow
     * control window will collapse. Consuming bytes enables the flow controller to send {@code WINDOW_UPDATE} to
     * restore a portion of the flow control window for the stream.
     * <p/>
     * If {@code stream} is {@code null} or closed (i.e. {@link Http2Stream#state()} method returns {@link
     * Http2Stream.State#CLOSED}), calling this method has no effect.
     *
     * @param stream the stream for which window space should be freed. The connection stream object must not be used.
     * If {@code stream} is {@code null} or closed (i.e. {@link Http2Stream#state()} method returns {@link
     * Http2Stream.State#CLOSED}), calling this method has no effect.
     * @param numBytes the number of bytes to be returned to the flow control window.
     * @return true if a {@code WINDOW_UPDATE} was sent, false otherwise.
     * @throws Http2Exception if the number of bytes returned exceeds the {@link #unconsumedBytes(Http2Stream)} for the
     * stream.
     */
    boolean consumeBytes(Http2Stream stream, int numBytes) throws Http2Exception;

    /**
     * The number of bytes for the given stream that have been received but not yet consumed by the
     * application.
     *
     * @param stream the stream for which window space should be freed.
     * @return the number of unconsumed bytes for the stream.
     */
    int unconsumedBytes(Http2Stream stream);

    /**
     * Get the initial flow control window size for the given stream. This quantity is measured in number of bytes. Note
     * the unavailable window portion can be calculated by {@link #initialWindowSize()} - {@link
     * #windowSize(Http2Stream)}.
     */
    int initialWindowSize(Http2Stream stream);
}

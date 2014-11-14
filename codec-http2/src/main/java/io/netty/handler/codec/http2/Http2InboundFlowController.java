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
import io.netty.channel.ChannelHandlerContext;

/**
 * Controls the inbound flow of data frames from the remote endpoint.
 */
public interface Http2InboundFlowController {

    /**
     * Applies inbound flow control to the given {@code DATA} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param data payload buffer for the frame.
     * @param padding the number of padding bytes found at the end of the frame.
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote
     *            endpoint for this stream.
     */
    void applyFlowControl(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endOfStream) throws Http2Exception;

    /**
     * Sets the initial inbound flow control window size and updates all stream window sizes by the
     * delta.
     *
     * @param newWindowSize the new initial window size.
     * @throws Http2Exception thrown if any protocol-related error occurred.
     */
    void initialInboundWindowSize(int newWindowSize) throws Http2Exception;

    /**
     * Gets the initial inbound flow control window size.
     */
    int initialInboundWindowSize();
}

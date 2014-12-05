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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * A {@link Http2FlowController} for controlling the flow of outbound {@code DATA} frames to the remote
 * endpoint.
 */
public interface Http2RemoteFlowController extends Http2FlowController {

    /**
     * Writes or queues a {@code DATA} frame for transmission to the remote endpoint. There is no
     * guarantee when the data will be written or whether it will be split into multiple frames
     * before sending. The returned future will only be completed once all of the data has been
     * successfully written to the remote endpoint.
     * <p>
     * Manually flushing the {@link ChannelHandlerContext} is not required, since the flow
     * controller will flush as appropriate.
     *
     * @param ctx the context from the handler.
     * @param stream the subject stream. Must not be the connection stream object.
     * @param data payload buffer for the frame.
     * @param padding the number of padding bytes to be added at the end of the frame.
     * @param endOfStream Indicates whether this is the last frame to be sent to the remote endpoint
     *            for this stream.
     * @param promise the promise to be completed when the data has been successfully written or a
     *            failure occurs.
     * @return a future that is completed when the frame is sent to the remote endpoint.
     */
    ChannelFuture sendFlowControlledFrame(ChannelHandlerContext ctx, Http2Stream stream,
            ByteBuf data, int padding, boolean endStream, ChannelPromise promise);

    /**
     * Gets the {@link ChannelFuture} for the most recent frame that was sent for the given stream
     * via a call to {@link #sendFlowControlledFrame()}. This is useful for cases such as ensuring
     * that {@code HEADERS} frames maintain send order with {@code DATA} frames.
     *
     * @param stream the subject stream. Must not be the connection stream object.
     * @return the most recent sent frame, or {@code null} if no frame has been sent for the stream.
     */
    ChannelFuture lastFlowControlledFrameSent(Http2Stream stream);
}

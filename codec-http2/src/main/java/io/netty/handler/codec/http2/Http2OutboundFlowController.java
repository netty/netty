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
 * Controls the outbound flow of data frames to the remote endpoint.
 */
public interface Http2OutboundFlowController extends Http2DataWriter {

    /**
     * Controls the flow-controlled writing of a DATA frame to the remote endpoint. There is no
     * guarantee when the data will be written or whether it will be split into multiple frames
     * before sending. The returned future will only be completed once all of the data has been
     * successfully written to the remote endpoint.
     * <p>
     * Manually flushing the {@link ChannelHandlerContext} is not required, since the flow
     * controller will flush as appropriate.
     */
    @Override
    ChannelFuture writeData(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            ByteBuf data, int padding, boolean endStream);

    /**
     * Sets the initial size of the connection's outbound flow control window. The outbound flow
     * control windows for all streams are updated by the delta in the initial window size. This is
     * called as part of the processing of a SETTINGS frame received from the remote endpoint.
     *
     * @param newWindowSize the new initial window size.
     */
    void initialOutboundWindowSize(int newWindowSize) throws Http2Exception;

    /**
     * Gets the initial size of the connection's outbound flow control window.
     */
    int initialOutboundWindowSize();

    /**
     * Updates the size of the stream's outbound flow control window. This is called upon receiving
     * a WINDOW_UPDATE frame from the remote endpoint.
     *
     * @param streamId the ID of the stream, or zero if the window is for the entire connection.
     * @param deltaWindowSize the change in size of the outbound flow control window.
     * @throws Http2Exception thrown if a protocol-related error occurred.
     */
    void updateOutboundWindowSize(int streamId, int deltaWindowSize) throws Http2Exception;
}

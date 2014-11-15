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

import io.netty.channel.ChannelHandlerContext;

/**
 * Allows data to be returned to the flow control window.
 */
public interface Http2FlowControlWindowManager {
    /**
     * Used by applications that participate in application-level inbound flow control. Allows the
     * application to return a number of bytes that has been processed and thereby enabling the
     * {@link Http2InboundFlowController} to send a {@code WINDOW_UPDATE} to restore at least part
     * of the flow control window.
     *
     * @param ctx the channel handler context to use when sending a {@code WINDOW_UPDATE} if
     *            appropriate
     * @param numBytes the number of bytes to be returned to the flow control window.
     */
    void returnProcessedBytes(ChannelHandlerContext ctx, int numBytes) throws Http2Exception;

    /**
     * The number of bytes that are outstanding and have not yet been returned to the flow controller.
     */
    int unProcessedBytes();

    /**
     * Get the stream that is being managed
     */
    Http2Stream stream();
}

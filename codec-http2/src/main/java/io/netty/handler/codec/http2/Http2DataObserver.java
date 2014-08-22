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
 * An observer of HTTP/2 {@code DATA} frames.
 */
public interface Http2DataObserver {

    /**
     * Handles an inbound {@code DATA} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param data payload buffer for the frame. If this buffer needs to be retained by the observer
     *            they must make a copy.
     * @param padding the number of padding bytes found at the end of the frame.
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote
     *            endpoint for this stream.
     */
    void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endOfStream) throws Http2Exception;
}

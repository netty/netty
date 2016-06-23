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
import io.netty.util.internal.UnstableApi;

/**
 * Interface that defines an object capable of producing HTTP/2 data frames.
 */
@UnstableApi
public interface Http2DataWriter {
    /**
     * Writes a {@code DATA} frame to the remote endpoint. This will result in one or more
     * frames being written to the context.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param data the payload of the frame. This will be released by this method.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive). A 1 byte padding is encoded as just the pad length field with value 0.
     *                A 256 byte padding is encoded as the pad length field with value 255 and 255 padding bytes
     *                appended to the end of the frame.
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeData(ChannelHandlerContext ctx, int streamId,
            ByteBuf data, int padding, boolean endStream, ChannelPromise promise);
}

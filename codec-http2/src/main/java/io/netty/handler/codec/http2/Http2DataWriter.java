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
 * Interface that defines an object capable of writing HTTP/2 data frames.
 */
public interface Http2DataWriter {

    /**
     * Writes a {@code DATA} frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param data the payload of the frame.
     * @param padding the amount of padding to be added to the end of the frame
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeData(ChannelHandlerContext ctx, int streamId,
            ByteBuf data, int padding, boolean endStream, ChannelPromise promise);
}

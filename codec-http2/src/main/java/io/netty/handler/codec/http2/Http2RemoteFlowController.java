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
 * A {@link Http2FlowController} for controlling the flow of outbound {@code DATA} frames to the remote
 * endpoint.
 */
public interface Http2RemoteFlowController extends Http2FlowController {

    /**
     * Writes or queues a payload for transmission to the remote endpoint. There is no
     * guarantee when the data will be written or whether it will be split into multiple frames
     * before sending.
     * <p>
     * Manually flushing the {@link ChannelHandlerContext} is not required, since the flow
     * controller will flush as appropriate.
     *
     * @param ctx the context from the handler.
     * @param stream the subject stream. Must not be the connection stream object.
     * @param payload payload to write subject to flow-control accounting and ordering rules.
     */
    void sendFlowControlled(ChannelHandlerContext ctx, Http2Stream stream, FlowControlled payload);

    /**
     * Implementations of this interface are used to progressively write chunks of the underlying
     * payload to the stream. A payload is considered to be fully written if {@link #write} has
     * been called at least once and it's {@link #size} is now zero.
     */
    interface FlowControlled {
        /**
         * The size of the payload in terms of bytes applied to the flow-control window.
         * Some payloads like {@code HEADER} frames have no cost against flow control and would
         * return 0 for this value even though they produce a non-zero number of bytes on
         * the wire. Other frames like {@code DATA} frames have both their payload and padding count
         * against flow-control.
         */
        int size();

        /**
         * Called to indicate that an error occurred before this object could be completely written.
         * <p>
         * The {@link Http2RemoteFlowController} will make exactly one call to either {@link #error(Throwable)} or
         * {@link #writeComplete()}.
         * </p>
         *
         * @param cause of the error.
         */
        void error(Throwable cause);

        /**
         * Called after this object has been successfully written.
         * <p>
         * The {@link Http2RemoteFlowController} will make exactly one call to either {@link #error(Throwable)} or
         * {@link #writeComplete()}.
         * </p>
         */
        void writeComplete();

        /**
         * Writes up to {@code allowedBytes} of the encapsulated payload to the stream. Note that
         * a value of 0 may be passed which will allow payloads with flow-control size == 0 to be
         * written. The flow-controller may call this method multiple times with different values until
         * the payload is fully written.
         * <p>
         * When an exception is thrown the {@link Http2RemoteFlowController} will make a call to
         * {@link #error(Throwable)}.
         * </p>
         *
         * @param allowedBytes an upper bound on the number of bytes the payload can write at this time.
         * @return {@code true} if a flush is required, {@code false} otherwise.
         */
        boolean write(int allowedBytes);
    }
}

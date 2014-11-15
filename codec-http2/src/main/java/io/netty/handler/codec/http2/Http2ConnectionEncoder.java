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
 * Handler for outbound HTTP/2 traffic.
 */
public interface Http2ConnectionEncoder extends Http2FrameWriter, Http2OutboundFlowController {

    /**
     * Builder for new instances of {@link Http2ConnectionEncoder}.
     */
    interface Builder {

        /**
         * Sets the {@link Http2Connection} to be used when building the encoder.
         */
        Builder connection(Http2Connection connection);

        /**
         * Sets the {@link Http2LifecycleManager} to be used when building the encoder.
         */
        Builder lifecycleManager(Http2LifecycleManager lifecycleManager);

        /**
         * Gets the {@link Http2LifecycleManager} to be used when building the encoder.
         */
        Http2LifecycleManager lifecycleManager();

        /**
         * Sets the {@link Http2FrameWriter} to be used when building the encoder.
         */
        Builder frameWriter(Http2FrameWriter frameWriter);

        /**
         * Sets the {@link Http2OutboundFlowController} to be used when building the encoder.
         */
        Builder outboundFlow(Http2OutboundFlowController outboundFlow);

        /**
         * Creates a new encoder instance.
         */
        Http2ConnectionEncoder build();
    }

    /**
     * Provides direct access to the underlying connection.
     */
    Http2Connection connection();

    /**
     * Provides direct access to the underlying frame writer object.
     */
    Http2FrameWriter frameWriter();

    /**
     * Gets the local settings on the top of the queue that has been sent but not ACKed. This may
     * return {@code null}.
     */
    Http2Settings pollSentSettings();

    /**
     * Sets the settings for the remote endpoint of the HTTP/2 connection.
     */
    void remoteSettings(Http2Settings settings) throws Http2Exception;

    /**
     * Writes the given data to the internal {@link Http2FrameWriter} without performing any
     * state checks on the connection/stream.
     */
    @Override
    ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
            Http2Flags flags, ByteBuf payload, ChannelPromise promise);
}

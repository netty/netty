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
 * Handler for outbound HTTP/2 traffic.
 */
@UnstableApi
public interface Http2ConnectionEncoder extends Http2FrameWriter {

    /**
     * Sets the lifecycle manager. Must be called as part of initialization before the encoder is used.
     */
    void lifecycleManager(Http2LifecycleManager lifecycleManager);

    /**
     * Provides direct access to the underlying connection.
     */
    Http2Connection connection();

    /**
     * Provides the remote flow controller for managing outbound traffic.
     */
    Http2RemoteFlowController flowController();

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

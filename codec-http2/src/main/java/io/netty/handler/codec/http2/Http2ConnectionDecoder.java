/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.UnstableApi;

import java.io.Closeable;
import java.util.List;

/**
 * Handler for inbound traffic on behalf of {@link Http2ConnectionHandler}. Performs basic protocol
 * conformance on inbound frames before calling the delegate {@link Http2FrameListener} for
 * application-specific processing. Note that frames of an unknown type (i.e. HTTP/2 extensions)
 * will skip all protocol checks and be given directly to the listener for processing.
 */
@UnstableApi
public interface Http2ConnectionDecoder extends Closeable {

    /**
     * Sets the lifecycle manager. Must be called as part of initialization before the decoder is used.
     */
    void lifecycleManager(Http2LifecycleManager lifecycleManager);

    /**
     * Provides direct access to the underlying connection.
     */
    Http2Connection connection();

    /**
     * Provides the local flow controller for managing inbound traffic.
     */
    Http2LocalFlowController flowController();

    /**
     * Set the {@link Http2FrameListener} which will be notified when frames are decoded.
     * <p>
     * This <strong>must</strong> be set before frames are decoded.
     */
    void frameListener(Http2FrameListener listener);

    /**
     * Get the {@link Http2FrameListener} which will be notified when frames are decoded.
     */
    Http2FrameListener frameListener();

    /**
     * Called by the {@link Http2ConnectionHandler} to decode the next frame from the input buffer.
     */
    void decodeFrame(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Http2Exception;

    /**
     * Gets the local settings for this endpoint of the HTTP/2 connection.
     */
    Http2Settings localSettings();

    /**
     * Indicates whether or not the first initial {@code SETTINGS} frame was received from the remote endpoint.
     */
    boolean prefaceReceived();

    @Override
    void close();
}

/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.http2;

import io.netty.channel.ChannelFuture;
import io.netty.util.internal.UnstableApi;

/**
 * A single stream within a HTTP/2 connection. To be used with the {@link Http2FrameCodec}.
 */
@UnstableApi
public interface Http2Stream2 {

    /**
     * The stream with identifier 0, representing the HTTP/2 connection.
     */
    Http2Stream2 CONNECTION_STREAM = new Http2Stream2() {

        @Override
        public Http2Stream2 id(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int id() {
            return 0;
        }

        @Override
        public Http2Stream2 managedState(Object state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object managedState() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture closeFuture() {
            throw new UnsupportedOperationException();
        }
    };

    /**
     * Set the stream identifier to a value greater than zero.
     *
     * <p>This method must never be called by user code, except it might be useful in tests. This method may be called
     * at most once.
     */
    Http2Stream2 id(int id);

    /**
     * Returns the stream identifier.
     *
     * <p>Use {@link Http2CodecUtil#isStreamIdValid(int)} to check if the stream has already been assigned an
     * identifier.
     */
    int id();

    /**
     * Attach application specific state to this HTTP/2 stream.
     *
     * <p>The state is maintained until the stream or the channel are closed (whatever happens first).
     */
    Http2Stream2 managedState(Object state);

    /**
     * Returns the application specific state object or {@code null} if no state has been attached yet.
     */
    Object managedState();

    /**
     * A {@link ChannelFuture} that will complete when a stream or the channel are closed (whatever happens first).
     *
     * <p>The {@link ChannelFuture} is guaranteed to be completed eventually, even if the stream never became active,
     * and will always succeed.
     */
    ChannelFuture closeFuture();
}

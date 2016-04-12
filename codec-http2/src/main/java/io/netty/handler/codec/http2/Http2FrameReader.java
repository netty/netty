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
import io.netty.util.internal.UnstableApi;

import java.io.Closeable;

/**
 * Reads HTTP/2 frames from an input {@link ByteBuf} and notifies the specified
 * {@link Http2FrameListener} when frames are complete.
 */
@UnstableApi
public interface Http2FrameReader extends Closeable {
    /**
     * Configuration specific to {@link Http2FrameReader}
     */
    interface Configuration {
        /**
         * Get the {@link Http2HeaderTable} for this {@link Http2FrameReader}
         */
        Http2HeaderTable headerTable();

        /**
         * Get the {@link Http2FrameSizePolicy} for this {@link Http2FrameReader}
         */
        Http2FrameSizePolicy frameSizePolicy();
    }

    /**
     * Attempts to read the next frame from the input buffer. If enough data is available to fully
     * read the frame, notifies the listener of the read frame.
     */
    void readFrame(ChannelHandlerContext ctx, ByteBuf input, Http2FrameListener listener)
            throws Http2Exception;

    /**
     * Get the configuration related elements for this {@link Http2FrameReader}
     */
    Configuration configuration();

    /**
     * Closes this reader and frees any allocated resources.
     */
    @Override
    void close();
}

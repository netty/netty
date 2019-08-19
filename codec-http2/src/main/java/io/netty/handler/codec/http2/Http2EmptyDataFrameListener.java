/*
 * Copyright 2019 The Netty Project
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
import io.netty.util.internal.ObjectUtil;

/**
 * Enforce a limit on the maximum number of consecutive empty DATA frames (without end_of_stream flag) that are allowed
 * before the connection will be closed.
 */
final class Http2EmptyDataFrameListener extends Http2FrameListenerDecorator {
    private final int maxConsecutiveEmptyFrames;

    private boolean violationDetected;
    private int emptyDataFrames;

    Http2EmptyDataFrameListener(Http2FrameListener listener, int maxConsecutiveEmptyFrames) {
        super(listener);
        this.maxConsecutiveEmptyFrames = ObjectUtil.checkPositive(
                maxConsecutiveEmptyFrames, "maxConsecutiveEmptyFrames");
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        if (endOfStream || data.isReadable()) {
            emptyDataFrames = 0;
        } else if (emptyDataFrames++ == maxConsecutiveEmptyFrames && !violationDetected) {
            violationDetected = true;
            throw Http2Exception.connectionError(Http2Error.ENHANCE_YOUR_CALM,
                    "Maximum number %d of empty data frames without end_of_stream flag received",
                    maxConsecutiveEmptyFrames);
        }

        return super.onDataRead(ctx, streamId, data, padding, endOfStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                              int padding, boolean endStream) throws Http2Exception {
        emptyDataFrames = 0;
        super.onHeadersRead(ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        emptyDataFrames = 0;
        super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
    }
}

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

/**
 * This class brings {@link Http2Connection.Listener} and {@link Http2FrameListener} together to provide
 * NOOP implementation so inheriting classes can selectively choose which methods to override.
 */
public class Http2EventAdapter implements Http2Connection.Listener, Http2FrameListener {
    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        return data.readableBytes() + padding;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream) throws Http2Exception {
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
            short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
            boolean exclusive) throws Http2Exception {
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) throws Http2Exception {
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
            throws Http2Exception {
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
            throws Http2Exception {
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
            ByteBuf payload) throws Http2Exception {
    }

    @Override
    public void onStreamAdded(Http2Stream stream) {
    }

    @Override
    public void onStreamActive(Http2Stream stream) {
    }

    @Override
    public void onStreamHalfClosed(Http2Stream stream) {
    }

    @Override
    public void onStreamClosed(Http2Stream stream) {
    }

    @Override
    public void onStreamRemoved(Http2Stream stream) {
    }

    @Override
    public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    }

    @Override
    public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    }
}

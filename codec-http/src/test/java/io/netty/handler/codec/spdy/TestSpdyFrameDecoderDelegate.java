/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;

import java.util.ArrayDeque;
import java.util.Queue;


final class TestSpdyFrameDecoderDelegate implements SpdyFrameDecoderDelegate {
    private final SpdyFrameDecoderDelegate delegate;

    private final Queue<ByteBuf> buffers = new ArrayDeque<ByteBuf>();

    TestSpdyFrameDecoderDelegate(final SpdyFrameDecoderDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public void readDataFrame(int streamId, boolean last, ByteBuf data) {
        delegate.readDataFrame(streamId, last, data);
        buffers.add(data);
    }

    @Override
    public void readSynStreamFrame(int streamId, int associatedToStreamId,
                                   byte priority, boolean last, boolean unidirectional) {
        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, last, unidirectional);
    }

    @Override
    public void readSynReplyFrame(int streamId, boolean last) {
        delegate.readSynReplyFrame(streamId, last);
    }

    @Override
    public void readRstStreamFrame(int streamId, int statusCode) {
        delegate.readRstStreamFrame(streamId, statusCode);
    }

    @Override
    public void readSettingsFrame(boolean clearPersisted) {
        delegate.readSettingsFrame(clearPersisted);
    }

    @Override
    public void readSetting(int id, int value, boolean persistValue, boolean persisted) {
        delegate.readSetting(id, value, persistValue, persisted);
    }

    @Override
    public void readSettingsEnd() {
        delegate.readSettingsEnd();
    }

    @Override
    public void readPingFrame(int id) {
        delegate.readPingFrame(id);
    }

    @Override
    public void readGoAwayFrame(int lastGoodStreamId, int statusCode) {
        delegate.readGoAwayFrame(lastGoodStreamId, statusCode);
    }

    @Override
    public void readHeadersFrame(int streamId, boolean last) {
        delegate.readHeadersFrame(streamId, last);
    }

    @Override
    public void readWindowUpdateFrame(int streamId, int deltaWindowSize) {
        delegate.readWindowUpdateFrame(streamId, deltaWindowSize);
    }

    @Override
    public void readHeaderBlock(ByteBuf headerBlock) {
        delegate.readHeaderBlock(headerBlock);
        buffers.add(headerBlock);
    }

    @Override
    public void readHeaderBlockEnd() {
        delegate.readHeaderBlockEnd();
    }

    @Override
    public void readFrameError(String message) {
        delegate.readFrameError(message);
    }

    @Override
    public void readUnknownFrame(final int frameType, final byte flags, final ByteBuf payload) {
        delegate.readUnknownFrame(frameType, flags, payload);
        buffers.add(payload);
    }

    void releaseAll() {
        for (;;) {
            ByteBuf buf = buffers.poll();
            if (buf == null) {
                return;
            }
            buf.release();
        }
    }
}

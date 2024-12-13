/*
 * Copyright 2014 The Netty Project
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

/**
 * Callback interface for {@link SpdyFrameDecoder}.
 */
public interface SpdyFrameDecoderDelegate {

    /**
     * Called when a DATA frame is received.
     */
    void readDataFrame(int streamId, boolean last, ByteBuf data);

    /**
     * Called when a SYN_STREAM frame is received.
     * The Name/Value Header Block is not included. See readHeaderBlock().
     */
    void readSynStreamFrame(
            int streamId, int associatedToStreamId, byte priority, boolean last, boolean unidirectional);

    /**
     * Called when a SYN_REPLY frame is received.
     * The Name/Value Header Block is not included. See readHeaderBlock().
     */
    void readSynReplyFrame(int streamId, boolean last);

    /**
     * Called when a RST_STREAM frame is received.
     */
    void readRstStreamFrame(int streamId, int statusCode);

    /**
     * Called when a SETTINGS frame is received.
     * Settings are not included. See readSetting().
     */
    void readSettingsFrame(boolean clearPersisted);

    /**
     * Called when an individual setting within a SETTINGS frame is received.
     */
    void readSetting(int id, int value, boolean persistValue, boolean persisted);

    /**
     * Called when the entire SETTINGS frame has been received.
     */
    void readSettingsEnd();

    /**
     * Called when a PING frame is received.
     */
    void readPingFrame(int id);

    /**
     * Called when a GOAWAY frame is received.
     */
    void readGoAwayFrame(int lastGoodStreamId, int statusCode);

    /**
     * Called when a HEADERS frame is received.
     * The Name/Value Header Block is not included. See readHeaderBlock().
     */
    void readHeadersFrame(int streamId, boolean last);

    /**
     * Called when a WINDOW_UPDATE frame is received.
     */
    void readWindowUpdateFrame(int streamId, int deltaWindowSize);

    /**
     * Called when the header block within a SYN_STREAM, SYN_REPLY, or HEADERS frame is received.
     */
    void readHeaderBlock(ByteBuf headerBlock);

    /**
     * Called when an entire header block has been received.
     */
    void readHeaderBlockEnd();

    /**
     * Called when an unrecoverable session error has occurred.
     */
    void readFrameError(String message);

    /**
     * Called when an unknown frame is received.
     *
     * @param frameType the frame type from the spdy header.
     * @param flags the flags in the frame header.
     * @param payload the payload of the frame.
     */
    default void readUnknownFrame(int frameType, byte flags, ByteBuf payload) {
        payload.release();
    }
}

/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.EmptyArrays;

/**
 * Web Socket Frame for closing the connection
 */
public class CloseWebSocketFrame extends WebSocketFrame {

    /**
     * Creates a new empty close frame.
     */
    public CloseWebSocketFrame() {
        super(Unpooled.buffer(0));
    }

    /**
     * Creates a new empty close frame with closing getStatus code and reason text
     *
     * @param statusCode
     *            Integer status code as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(int statusCode, String reasonText) {
        this(true, 0, statusCode, reasonText);
    }

    /**
     * Creates a new close frame with no losing getStatus code and no reason text
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     */
    public CloseWebSocketFrame(boolean finalFragment, int rsv) {
        this(finalFragment, rsv, Unpooled.buffer(0));
    }

    /**
     * Creates a new close frame with closing status code and reason text
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param statusCode
     *            Integer status code as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(boolean finalFragment, int rsv, int statusCode, String reasonText) {
        super(finalFragment, rsv, newBinaryData(statusCode, reasonText));
    }

    private static ByteBuf newBinaryData(int statusCode, String reasonText) {
        byte[] reasonBytes = EmptyArrays.EMPTY_BYTES;
        if (reasonText != null) {
            reasonBytes = reasonText.getBytes(CharsetUtil.UTF_8);
        }

        ByteBuf binaryData = Unpooled.buffer(2 + reasonBytes.length);
        binaryData.writeShort(statusCode);
        if (reasonBytes.length > 0) {
            binaryData.writeBytes(reasonBytes);
        }

        binaryData.readerIndex(0);
        return binaryData;
    }

    /**
     * Creates a new close frame
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param binaryData
     *            the content of the frame. Must be 2 byte integer followed by optional UTF-8 encoded string.
     */
    public CloseWebSocketFrame(boolean finalFragment, int rsv, ByteBuf binaryData) {
        super(finalFragment, rsv, binaryData);
    }

    /**
     * Returns the closing status code as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. If
     * a getStatus code is set, -1 is returned.
     */
    public int statusCode() {
        ByteBuf binaryData = content();
        if (binaryData == null || binaryData.capacity() == 0) {
            return -1;
        }

        binaryData.readerIndex(0);
        int statusCode = binaryData.readShort();
        binaryData.readerIndex(0);

        return statusCode;
    }

    /**
     * Returns the reason text as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a> If a reason
     * text is not supplied, an empty string is returned.
     */
    public String reasonText() {
        ByteBuf binaryData = content();
        if (binaryData == null || binaryData.capacity() <= 2) {
            return "";
        }

        binaryData.readerIndex(2);
        String reasonText = binaryData.toString(CharsetUtil.UTF_8);
        binaryData.readerIndex(0);

        return reasonText;
    }

    @Override
    public CloseWebSocketFrame copy() {
        return (CloseWebSocketFrame) super.copy();
    }

    @Override
    public CloseWebSocketFrame duplicate() {
        return (CloseWebSocketFrame) super.duplicate();
    }

    @Override
    public CloseWebSocketFrame retainedDuplicate() {
        return (CloseWebSocketFrame) super.retainedDuplicate();
    }

    @Override
    public CloseWebSocketFrame replace(ByteBuf content) {
        return new CloseWebSocketFrame(isFinalFragment(), rsv(), content);
    }

    @Override
    public CloseWebSocketFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public CloseWebSocketFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public CloseWebSocketFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public CloseWebSocketFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }
}

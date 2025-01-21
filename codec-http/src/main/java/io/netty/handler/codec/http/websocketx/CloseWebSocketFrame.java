/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

/**
 * Web Socket Frame for closing the connection.
 */
public class CloseWebSocketFrame extends WebSocketFrame {

    /**
     * Creates a new empty close frame.
     */
    public CloseWebSocketFrame() {
        super(Unpooled.buffer(0));
    }

    /**
     * Creates a new empty close frame with closing status code and reason text
     *
     * @param status
     *            Status code as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     */
    public CloseWebSocketFrame(WebSocketCloseStatus status) {
        this(requireValidStatusCode(status.code()), status.reasonText());
    }

    /**
     * Creates a new empty close frame with closing status code and reason text
     *
     * @param status
     *            Status code as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(WebSocketCloseStatus status, String reasonText) {
        this(requireValidStatusCode(status.code()), reasonText);
    }

    /**
     * Creates a new empty close frame with closing status code and reason text
     *
     * @param statusCode
     *            Integer status code as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(int statusCode, String reasonText) {
        this(true, 0, requireValidStatusCode(statusCode), reasonText);
    }

    /**
     * Creates a new close frame with no losing status code and no reason text
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions.
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
     *            Integer status code as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(boolean finalFragment, int rsv, int statusCode, String reasonText) {
        super(finalFragment, rsv, newBinaryData(requireValidStatusCode(statusCode), reasonText));
    }

    private static ByteBuf newBinaryData(int statusCode, String reasonText) {
        if (reasonText == null) {
            reasonText = StringUtil.EMPTY_STRING;
        }

        ByteBuf binaryData = Unpooled.buffer(2 + reasonText.length());
        binaryData.writeShort(statusCode);
        if (!reasonText.isEmpty()) {
            binaryData.writeCharSequence(reasonText, CharsetUtil.UTF_8);
        }
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
     * Returns the closing status code as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. If
     * a status code is set, -1 is returned.
     */
    public int statusCode() {
        ByteBuf binaryData = content();
        if (binaryData == null || binaryData.readableBytes() < 2) {
            return -1;
        }

        return binaryData.getUnsignedShort(binaryData.readerIndex());
    }

    /**
     * Returns the reason text as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a> If a reason
     * text is not supplied, an empty string is returned.
     */
    public String reasonText() {
        ByteBuf binaryData = content();
        if (binaryData == null || binaryData.readableBytes() <= 2) {
            return "";
        }

        return binaryData.toString(binaryData.readerIndex() + 2, binaryData.readableBytes() - 2, CharsetUtil.UTF_8);
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

    static int requireValidStatusCode(int statusCode) {
        if (WebSocketCloseStatus.isValidStatusCode(statusCode)) {
            return statusCode;
        } else {
            throw new IllegalArgumentException("WebSocket close status code does NOT comply with RFC-6455: " +
                    statusCode);
        }
    }
}

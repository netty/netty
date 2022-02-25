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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.CharsetUtil;
import io.netty5.util.internal.StringUtil;

import java.nio.charset.StandardCharsets;

/**
 * Web Socket Frame for closing the connection.
 */
public class CloseWebSocketFrame extends WebSocketFrame {
    /**
     * Creates a new empty close frame with closing status code and reason text
     *
     * @param allocator {@link BufferAllocator} to use for allocating data.
     * @param status Status code as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     */
    public CloseWebSocketFrame(BufferAllocator allocator, WebSocketCloseStatus status) {
        this(allocator, requireValidStatusCode(status.code()), status.reasonText());
    }

    /**
     * Creates a new empty close frame with closing status code and reason text
     *
     * @param allocator {@link BufferAllocator} to use for allocating data.
     * @param status Status code as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(BufferAllocator allocator, WebSocketCloseStatus status, String reasonText) {
        this(allocator, requireValidStatusCode(status.code()), reasonText);
    }

    /**
     * Creates a new empty close frame with closing status code and reason text
     *
     * @param allocator {@link BufferAllocator} to use for allocating data.
     * @param statusCode Integer status code as per <a href=
     * "https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For example, <tt>1000</tt> indicates normal
     * closure.
     * @param reasonText Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(BufferAllocator allocator, int statusCode, String reasonText) {
        this(allocator, true, 0, requireValidStatusCode(statusCode), reasonText);
    }

    /**
     * Creates a new close frame with no losing status code and no reason text
     *
     * @param allocator {@link BufferAllocator} to use for allocating data.
     * @param finalFragment flag indicating if this frame is the final fragment
     * @param rsv reserved bits used for protocol extensions.
     */
    public CloseWebSocketFrame(BufferAllocator allocator, boolean finalFragment, int rsv) {
        this(finalFragment, rsv, allocator.allocate(0));
    }

    /**
     * Creates a new close frame with closing status code and reason text
     *
     * @param finalFragment flag indicating if this frame is the final fragment
     * @param rsv reserved bits used for protocol extensions
     * @param statusCode Integer status code as per <a href=
     * "https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For example, <tt>1000</tt> indicates normal
     * closure.
     * @param reasonText Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(BufferAllocator allocator, boolean finalFragment, int rsv, int statusCode,
                               String reasonText) {
        super(finalFragment, rsv, newBinaryData(allocator, requireValidStatusCode(statusCode), reasonText));
    }

    private static Buffer newBinaryData(BufferAllocator allocator, short statusCode, String reasonText) {
        if (reasonText == null) {
            reasonText = StringUtil.EMPTY_STRING;
        }

        final Buffer binaryData;
        if (!reasonText.isEmpty()) {
            byte[] reasonTextBytes = reasonText.getBytes(StandardCharsets.UTF_8);
            binaryData = allocator.allocate(2 + reasonTextBytes.length);
            binaryData.writeShort(statusCode);
            binaryData.writeBytes(reasonTextBytes);
        } else {
            binaryData = allocator.allocate(2).writeShort(statusCode);
        }

        return binaryData;
    }

    /**
     * Creates a new close frame
     *
     * @param finalFragment flag indicating if this frame is the final fragment
     * @param rsv reserved bits used for protocol extensions
     * @param binaryData the content of the frame. Must be 2 byte integer followed by optional UTF-8 encoded string.
     */
    public CloseWebSocketFrame(boolean finalFragment, int rsv, Buffer binaryData) {
        super(finalFragment, rsv, binaryData);
    }

    private CloseWebSocketFrame(CloseWebSocketFrame copyFrom, Buffer data) {
        super(copyFrom, data);
    }

    /**
     * Returns the closing status code as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. If
     * a status code is set, -1 is returned.
     */
    public int statusCode() {
        Buffer binaryData = binaryData();
        if (binaryData == null || binaryData.readableBytes() < 2) {
            return -1;
        }

        return binaryData.getShort(binaryData.readerOffset());
    }

    /**
     * Returns the reason text as per <a href="https://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a> If a reason
     * text is not supplied, an empty string is returned.
     */
    public String reasonText() {
        Buffer binaryData = binaryData();
        if (binaryData == null || binaryData.readableBytes() <= 2) {
            return "";
        }

        int base = binaryData.readerOffset();
        try {
            binaryData.skipReadable(2);
            return binaryData.toString(CharsetUtil.UTF_8);
        } finally {
            binaryData.readerOffset(base);
        }
    }

    @Override
    protected WebSocketFrame receive(Buffer buf) {
        return new CloseWebSocketFrame(this, buf);
    }

    static short requireValidStatusCode(int statusCode) {
        if (WebSocketCloseStatus.isValidStatusCode(statusCode)) {
            return (short) statusCode;
        } else {
            throw new IllegalArgumentException(
                    "WebSocket close status code does NOT comply with RFC-6455: " + statusCode);
        }
    }
}

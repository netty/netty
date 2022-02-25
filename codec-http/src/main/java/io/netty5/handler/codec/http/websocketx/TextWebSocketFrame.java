/*
 * Copyright 2012 The Netty Project
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

/**
 * Web Socket text frame.
 */
public class TextWebSocketFrame extends WebSocketFrame {
    /**
     * Creates a new text frame with the specified text string. The final fragment flag is set to true.
     *
     * @param allocator {@link BufferAllocator} to use for allocating data.
     * @param text String to put in the frame.
     */
    public TextWebSocketFrame(BufferAllocator allocator, String text) {
        super(fromText(allocator, text));
    }

    /**
     * Creates a new text frame with the specified binary data. The final fragment flag is set to true.
     *
     * @param binaryData the content of the frame.
     */
    public TextWebSocketFrame(Buffer binaryData) {
        super(binaryData);
    }

    /**
     * Creates a new text frame with the specified text string. The final fragment flag is set to true.
     *
     * @param allocator {@link BufferAllocator} to use for allocating data.
     * @param finalFragment flag indicating if this frame is the final fragment
     * @param rsv reserved bits used for protocol extensions
     * @param text String to put in the frame.
     */
    public TextWebSocketFrame(BufferAllocator allocator, boolean finalFragment, int rsv, String text) {
        super(finalFragment, rsv, fromText(allocator, text));
    }

    private TextWebSocketFrame(TextWebSocketFrame copyFrom, Buffer data) {
        super(copyFrom, data);
    }

    private static Buffer fromText(BufferAllocator allocator, String text) {
        if (text == null || text.isEmpty()) {
            return allocator.allocate(0);
        } else {
            return allocator.copyOf(text.getBytes(CharsetUtil.UTF_8));
        }
    }

    /**
     * Creates a new text frame with the specified binary data and the final fragment flag.
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param binaryData
     *            the content of the frame.
     */
    public TextWebSocketFrame(boolean finalFragment, int rsv, Buffer binaryData) {
        super(finalFragment, rsv, binaryData);
    }

    /**
     * Returns the text data in this frame.
     */
    public String text() {
        return binaryData().toString(CharsetUtil.UTF_8);
    }

    @Override
    protected WebSocketFrame receive(Buffer buf) {
        return new TextWebSocketFrame(this, buf);
    }
}

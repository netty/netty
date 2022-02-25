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
 * Web Socket continuation frame containing continuation text or binary data. This is used for
 * fragmented messages where the contents of a messages is contained more than 1 frame.
 */
public class ContinuationWebSocketFrame extends WebSocketFrame {
    /**
     * Creates a new continuation frame with the specified binary data. The final fragment flag is
     * set to true.
     *
     * @param binaryData the content of the frame.
     */
    public ContinuationWebSocketFrame(Buffer binaryData) {
        super(binaryData);
    }

    /**
     * Creates a new continuation frame with the specified binary data.
     *
     * @param finalFragment flag indicating if this frame is the final fragment
     * @param rsv reserved bits used for protocol extensions
     * @param binaryData the content of the frame.
     */
    public ContinuationWebSocketFrame(boolean finalFragment, int rsv, Buffer binaryData) {
        super(finalFragment, rsv, binaryData);
    }

    /**
     * Creates a new continuation frame with the specified text data
     *
     * @param allocator {@link BufferAllocator} to use for allocating data.
     * @param finalFragment flag indicating if this frame is the final fragment
     * @param rsv reserved bits used for protocol extensions
     * @param text text content of the frame.
     */
    public ContinuationWebSocketFrame(BufferAllocator allocator, boolean finalFragment, int rsv, String text) {
        this(finalFragment, rsv, fromText(allocator, text));
    }

    private ContinuationWebSocketFrame(ContinuationWebSocketFrame copyFrom, Buffer data) {
        super(copyFrom, data);
    }

    /**
     * Returns the text data in this frame.
     */
    public String text() {
        return binaryData().toString(CharsetUtil.UTF_8);
    }

    /**
     * Sets the string for this frame.
     *
     * @param text text to store.
     */
    private static Buffer fromText(BufferAllocator allocator, String text) {
        if (text == null || text.isEmpty()) {
            return allocator.allocate(0);
        } else {
            return allocator.copyOf(text.getBytes(CharsetUtil.UTF_8));
        }
    }

    @Override
    protected WebSocketFrame receive(Buffer buf) {
        return new ContinuationWebSocketFrame(this, buf);
    }
}

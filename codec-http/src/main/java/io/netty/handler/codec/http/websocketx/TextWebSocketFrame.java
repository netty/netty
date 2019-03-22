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

/**
 * Web Socket text frame.
 */
public class TextWebSocketFrame extends WebSocketFrame {

    /**
     * Creates a new empty text frame.
     */
    public TextWebSocketFrame() {
        super(Unpooled.buffer(0));
    }

    /**
     * Creates a new text frame with the specified text string. The final fragment flag is set to true.
     *
     * @param text
     *            String to put in the frame.
     */
    public TextWebSocketFrame(String text) {
        super(fromText(text));
    }

    /**
     * Creates a new text frame with the specified binary data. The final fragment flag is set to true.
     *
     * @param binaryData
     *            the content of the frame.
     */
    public TextWebSocketFrame(ByteBuf binaryData) {
        super(binaryData);
    }

    /**
     * Creates a new text frame with the specified text string. The final fragment flag is set to true.
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param text
     *            String to put in the frame.
     */
    public TextWebSocketFrame(boolean finalFragment, int rsv, String text) {
        super(finalFragment, rsv, fromText(text));
    }

    private static ByteBuf fromText(String text) {
        if (text == null || text.isEmpty()) {
            return Unpooled.EMPTY_BUFFER;
        } else {
            return Unpooled.copiedBuffer(text, CharsetUtil.UTF_8);
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
    public TextWebSocketFrame(boolean finalFragment, int rsv, ByteBuf binaryData) {
        super(finalFragment, rsv, binaryData);
    }

    /**
     * Returns the text data in this frame.
     */
    public String text() {
        return content().toString(CharsetUtil.UTF_8);
    }

    @Override
    public TextWebSocketFrame copy() {
        return (TextWebSocketFrame) super.copy();
    }

    @Override
    public TextWebSocketFrame duplicate() {
        return (TextWebSocketFrame) super.duplicate();
    }

    @Override
    public TextWebSocketFrame retainedDuplicate() {
        return (TextWebSocketFrame) super.retainedDuplicate();
    }

    @Override
    public TextWebSocketFrame replace(ByteBuf content) {
        return new TextWebSocketFrame(isFinalFragment(), rsv(), content);
    }

    @Override
    public TextWebSocketFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public TextWebSocketFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public TextWebSocketFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public TextWebSocketFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }
}

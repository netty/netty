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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Web Socket frame containing binary data.
 */
public class BinaryWebSocketFrame extends WebSocketFrame {

    /**
     * Creates a new empty binary frame.
     */
    public BinaryWebSocketFrame() {
        super(Unpooled.buffer(0));
    }

    /**
     * Creates a new binary frame with the specified binary data. The final fragment flag is set to true.
     *
     * @param binaryData
     *            the content of the frame.
     */
    public BinaryWebSocketFrame(ByteBuf binaryData) {
        super(binaryData);
    }

    /**
     * Creates a new binary frame with the specified binary data and the final fragment flag.
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param binaryData
     *            the content of the frame.
     */
    public BinaryWebSocketFrame(boolean finalFragment, int rsv, ByteBuf binaryData) {
        super(finalFragment, rsv, binaryData);
    }

    @Override
    public BinaryWebSocketFrame copy() {
        return (BinaryWebSocketFrame) super.copy();
    }

    @Override
    public BinaryWebSocketFrame duplicate() {
        return (BinaryWebSocketFrame) super.duplicate();
    }

    @Override
    public BinaryWebSocketFrame retainedDuplicate() {
        return (BinaryWebSocketFrame) super.retainedDuplicate();
    }

    @Override
    public BinaryWebSocketFrame replace(ByteBuf content) {
        return new BinaryWebSocketFrame(isFinalFragment(), rsv(), content);
    }

    @Override
    public BinaryWebSocketFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public BinaryWebSocketFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public BinaryWebSocketFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public BinaryWebSocketFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }
}

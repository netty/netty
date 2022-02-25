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
import io.netty5.buffer.api.Send;

/**
 * Web Socket frame containing binary data.
 */
public class PongWebSocketFrame extends WebSocketFrame {
    /**
     * Creates a new pong frame with the specified binary data.
     *
     * @param binaryData the content of the frame.
     */
    public PongWebSocketFrame(Buffer binaryData) {
        super(binaryData);
    }

    /**
     * Creates a new pong frame with the specified binary data.
     *
     * @param binaryData the content of the frame.
     */
    public PongWebSocketFrame(Send<Buffer> binaryData) {
        super(binaryData);
    }

    /**
     * Creates a new pong frame with the specified binary data
     *
     * @param finalFragment flag indicating if this frame is the final fragment
     * @param rsv reserved bits used for protocol extensions
     * @param binaryData the content of the frame.
     */
    public PongWebSocketFrame(boolean finalFragment, int rsv, Buffer binaryData) {
        super(finalFragment, rsv, binaryData);
    }

    private PongWebSocketFrame(PongWebSocketFrame copyFrom, Buffer data) {
        super(copyFrom, data);
    }

    @Override
    protected WebSocketFrame receive(Buffer buf) {
        return new PongWebSocketFrame(this, buf);
    }
}

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

import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.DecoderException;

/**
 * An {@link DecoderException} which is thrown when the received {@link WebSocketFrame} data could not be decoded by
 * an inbound handler.
 */
public final class CorruptedWebSocketFrameException extends CorruptedFrameException {

    private static final long serialVersionUID = 3918055132492988338L;

    private final WebSocketCloseStatus closeStatus;

    /**
     * Creates a new instance.
     */
    public CorruptedWebSocketFrameException() {
        this(WebSocketCloseStatus.PROTOCOL_ERROR, null, null);
    }

    /**
     * Creates a new instance.
     */
    public CorruptedWebSocketFrameException(WebSocketCloseStatus status, String message, Throwable cause) {
        super(message == null ? status.reasonText() : message, cause);
        closeStatus = status;
    }

    /**
     * Creates a new instance.
     */
    public CorruptedWebSocketFrameException(WebSocketCloseStatus status, String message) {
        this(status, message, null);
    }

    /**
     * Creates a new instance.
     */
    public CorruptedWebSocketFrameException(WebSocketCloseStatus status, Throwable cause) {
        this(status, null, cause);
    }

    public WebSocketCloseStatus closeStatus() {
        return closeStatus;
    }

}

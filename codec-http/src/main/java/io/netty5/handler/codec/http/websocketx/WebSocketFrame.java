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
import io.netty5.buffer.api.BufferHolder;
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.Send;
import io.netty5.util.internal.StringUtil;

import static java.nio.charset.Charset.defaultCharset;

/**
 * Base class for web socket frames.
 */
public abstract class WebSocketFrame extends BufferHolder<WebSocketFrame> {

    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    private final boolean finalFragment;

    /**
     * RSV1, RSV2, RSV3 used for extensions
     */
    private final int rsv;

    protected WebSocketFrame(Buffer binaryData) {
        this(true, 0, binaryData);
    }

    protected WebSocketFrame(Send<Buffer> binaryData) {
        super(binaryData);
        finalFragment = true;
        rsv = 0;
    }

    protected WebSocketFrame(boolean finalFragment, int rsv, Buffer binaryData) {
        super(binaryData);
        this.finalFragment = finalFragment;
        this.rsv = rsv;
    }

    /**
     * This is a copy-constructor, used by sub-classes to implement {@link Resource#send()}.
     * The life cycle of the {@code binaryData} buffer is not manipulated by this constructor, but instead stored as-is.
     *
     * @param copyFrom The original frame instance to copy from.
     * @param binaryData The binary data of the original frame.
     */
    protected WebSocketFrame(WebSocketFrame copyFrom, Buffer binaryData) {
        super(binaryData);
        finalFragment = copyFrom.finalFragment;
        rsv = copyFrom.rsv;
    }

    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    public boolean isFinalFragment() {
        return finalFragment;
    }

    /**
     * Bits used for extensions to the standard.
     */
    public int rsv() {
        return rsv;
    }

    public Buffer binaryData() {
        return getBuffer();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(data: " + getBuffer().toString(defaultCharset()) + ')';
    }
}

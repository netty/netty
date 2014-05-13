/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.protocol;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;

/**
 * A PreludeFrame the first message sent by the
 * <a href="http://sockjs.github.io/sockjs-protocol/sockjs-protocol-0.3.3.html#section-85">xhr-streaming</a> protocol.
 */
public class PreludeFrame extends DefaultByteBufHolder implements Frame {

    static final int CONTENT_SIZE = 2048;
    private static final ByteBuf PRELUDE_FRAME = unreleasableBuffer(generateContent());

    public PreludeFrame() {
        super(PRELUDE_FRAME.duplicate());
    }

    public static ByteBuf generateContent() {
        final ByteBuf buf = Unpooled.buffer(CONTENT_SIZE);
        for (int i = 0; i < CONTENT_SIZE; i++) {
            buf.writeByte('h');
        }
        return buf;
    }

    @Override
    public PreludeFrame copy() {
        return new PreludeFrame();
    }

    @Override
    public PreludeFrame duplicate() {
        return new PreludeFrame();
    }

    @Override
    public PreludeFrame retain() {
        PRELUDE_FRAME.retain();
        return this;
    }

    @Override
    public PreludeFrame retain(int increment) {
        PRELUDE_FRAME.retain(increment);
        return this;
    }

}

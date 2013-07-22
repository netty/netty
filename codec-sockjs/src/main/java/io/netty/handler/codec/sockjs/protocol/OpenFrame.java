/*
 * Copyright 2012 The Netty Project
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

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.CharsetUtil;

/**
 * Everytime a new session is estabilshed with the server the server must sent an OpenFrame in accordance with
 *  <a href="http://sockjs.github.io/sockjs-protocol/sockjs-protocol-0.3.3.html#section-42">Protocol and Framing</a>.
 */
public class OpenFrame extends DefaultByteBufHolder implements Frame {

    public static final String OPEN = "o";
    private static final ByteBuf OPEN_FRAME = unreleasableBuffer(copiedBuffer(OPEN, CharsetUtil.UTF_8));

    public OpenFrame() {
        super(OPEN_FRAME.duplicate());
    }

    @Override
    public String toString() {
        return "OpenFrame[o]";
    }

}

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

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

/**
 * A heartbeat frame is sent by the server to keep the connection from breaking.
 */
public class HeartbeatFrame extends DefaultByteBufHolder implements Frame {

    private static final String HEARTBEAT = "h";
    private static final ByteBuf HEARTBEAT_FRAME = unreleasableBuffer(copiedBuffer(HEARTBEAT, CharsetUtil.UTF_8));

    public HeartbeatFrame() {
        super(HEARTBEAT_FRAME.duplicate());
    }

    @Override
    public HeartbeatFrame copy() {
        return new HeartbeatFrame();
    }

    @Override
    public HeartbeatFrame duplicate() {
        return new HeartbeatFrame();
    }

    @Override
    public HeartbeatFrame retain() {
        HEARTBEAT_FRAME.retain();
        return this;
    }

    @Override
    public HeartbeatFrame retain(int increment) {
        HEARTBEAT_FRAME.retain(increment);
        return this;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[h]";
    }

}

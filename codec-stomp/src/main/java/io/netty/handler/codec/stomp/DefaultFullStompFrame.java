/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

/**
 * Default implementation of {@link FullStompFrame}.
 */
public class DefaultFullStompFrame extends DefaultStompFrame implements FullStompFrame {
    private final ByteBuf content;

    public DefaultFullStompFrame(StompCommand command) {
        this(command, Unpooled.buffer(0));
        if (command == null) {
            throw new NullPointerException("command");
        }
    }

    public DefaultFullStompFrame(StompCommand command, ByteBuf content) {
        super(command);
        if (content == null) {
            throw new NullPointerException("content");
        }
        this.content = content;
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public FullStompFrame copy() {
        return new DefaultFullStompFrame(command, content.copy());
    }

    @Override
    public FullStompFrame duplicate() {
        return new DefaultFullStompFrame(command, content.duplicate());
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public FullStompFrame retain() {
        content.retain();
        return this;
    }

    @Override
    public FullStompFrame retain(int increment) {
        content.retain();
        return this;
    }

    @Override
    public FullStompFrame touch() {
        content.touch();
        return this;
    }

    @Override
    public FullStompFrame touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(int decrement) {
        return content.release(decrement);
    }

    @Override
    public String toString() {
        return "DefaultFullStompFrame{" +
            "command=" + command +
            ", headers=" + headers +
            ", content=" + content.toString(CharsetUtil.UTF_8) +
            '}';
    }
}

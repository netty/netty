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
 * Default implementation of {@link StompFrame}.
 */
public class DefaultStompFrame extends DefaultStompHeadersSubframe implements StompFrame {

    private final ByteBuf content;

    public DefaultStompFrame(StompCommand command) {
        this(command, Unpooled.buffer(0));
    }

    public DefaultStompFrame(StompCommand command, ByteBuf content) {
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
    public StompFrame copy() {
        return replace(content.copy());
    }

    @Override
    public StompFrame duplicate() {
        return replace(content.duplicate());
    }

    @Override
    public StompFrame retainedDuplicate() {
        return replace(content.retainedDuplicate());
    }

    @Override
    public StompFrame replace(ByteBuf content) {
        return new DefaultStompFrame(command, content);
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public StompFrame retain() {
        content.retain();
        return this;
    }

    @Override
    public StompFrame retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public StompFrame touch() {
        content.touch();
        return this;
    }

    @Override
    public StompFrame touch(Object hint) {
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
        return "DefaultStompFrame{" +
            "command=" + command +
            ", headers=" + headers +
            ", content=" + content.toString(CharsetUtil.UTF_8) +
            '}';
    }
}

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

/**
 * The default implementation for the {@link LastStompContent}.
 */
public class DefaultLastStompContent extends DefaultStompContent implements LastStompContent {
    public DefaultLastStompContent(ByteBuf content) {
        super(content);
    }

    @Override
    public DefaultLastStompContent retain() {
        super.retain();
        return this;
    }

    @Override
    public LastStompContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public LastStompContent touch() {
        super.touch();
        return this;
    }

    @Override
    public LastStompContent touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public LastStompContent copy() {
        return new DefaultLastStompContent(content().copy());
    }

    @Override
    public LastStompContent duplicate() {
        return new DefaultLastStompContent(content().duplicate());
    }

    @Override
    public String toString() {
        return "DefaultLastStompContent{" +
                "decoderResult=" + getDecoderResult() +
                '}';
    }

}

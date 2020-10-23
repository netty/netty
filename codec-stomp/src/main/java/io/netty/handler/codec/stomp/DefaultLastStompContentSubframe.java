/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import io.netty.buffer.ByteBuf;

/**
 * The default implementation for the {@link LastStompContentSubframe}.
 */
public class DefaultLastStompContentSubframe extends DefaultStompContentSubframe implements LastStompContentSubframe {

    public DefaultLastStompContentSubframe(ByteBuf content) {
        super(content);
    }

    @Override
    public LastStompContentSubframe copy() {
        return (LastStompContentSubframe) super.copy();
    }

    @Override
    public LastStompContentSubframe duplicate() {
        return (LastStompContentSubframe) super.duplicate();
    }

    @Override
    public LastStompContentSubframe retainedDuplicate() {
        return (LastStompContentSubframe) super.retainedDuplicate();
    }

    @Override
    public LastStompContentSubframe replace(ByteBuf content) {
        return new DefaultLastStompContentSubframe(content);
    }

    @Override
    public DefaultLastStompContentSubframe retain() {
        super.retain();
        return this;
    }

    @Override
    public LastStompContentSubframe retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public LastStompContentSubframe touch() {
        super.touch();
        return this;
    }

    @Override
    public LastStompContentSubframe touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public String toString() {
        return "DefaultLastStompContent{" +
                "decoderResult=" + decoderResult() +
                '}';
    }
}

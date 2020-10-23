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
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.handler.codec.DecoderResult;

/**
 * The default {@link StompContentSubframe} implementation.
 */
public class DefaultStompContentSubframe extends DefaultByteBufHolder implements StompContentSubframe {
    private DecoderResult decoderResult = DecoderResult.SUCCESS;

    public DefaultStompContentSubframe(ByteBuf content) {
        super(content);
    }

    @Override
    public StompContentSubframe copy() {
        return (StompContentSubframe) super.copy();
    }

    @Override
    public StompContentSubframe duplicate() {
        return (StompContentSubframe) super.duplicate();
    }

    @Override
    public StompContentSubframe retainedDuplicate() {
        return (StompContentSubframe) super.retainedDuplicate();
    }

    @Override
    public StompContentSubframe replace(ByteBuf content) {
        return new DefaultStompContentSubframe(content);
    }

    @Override
    public StompContentSubframe retain() {
        super.retain();
        return this;
    }

    @Override
    public StompContentSubframe retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public StompContentSubframe touch() {
        super.touch();
        return this;
    }

    @Override
    public StompContentSubframe touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public DecoderResult decoderResult() {
        return decoderResult;
    }

    @Override
    public void setDecoderResult(DecoderResult decoderResult) {
        this.decoderResult = decoderResult;
    }

    @Override
    public String toString() {
        return "DefaultStompContent{" +
            "decoderResult=" + decoderResult +
            '}';
    }
}

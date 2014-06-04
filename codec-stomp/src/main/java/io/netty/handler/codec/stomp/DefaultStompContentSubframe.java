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
import io.netty.handler.codec.DecoderResult;

/**
 * The default {@link StompContentSubframe} implementation.
 */
public class DefaultStompContentSubframe implements StompContentSubframe {
    private DecoderResult decoderResult = DecoderResult.SUCCESS;
    private final ByteBuf content;

    public DefaultStompContentSubframe(ByteBuf content) {
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
    public StompContentSubframe copy() {
        return new DefaultStompContentSubframe(content().copy());
    }

    @Override
    public StompContentSubframe duplicate() {
        return new DefaultStompContentSubframe(content().duplicate());
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public StompContentSubframe retain() {
        content().retain();
        return this;
    }

    @Override
    public StompContentSubframe retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public StompContentSubframe touch() {
        content.touch();
        return this;
    }

    @Override
    public StompContentSubframe touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content().release();
    }

    @Override
    public boolean release(int decrement) {
        return content().release(decrement);
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

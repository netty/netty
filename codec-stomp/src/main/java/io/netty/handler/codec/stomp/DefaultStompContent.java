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
 * The default {@link StompContent} implementation.
 */
public class DefaultStompContent implements StompContent {
    private DecoderResult decoderResult;
    private final ByteBuf content;

    public DefaultStompContent(ByteBuf content) {
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
    public StompContent copy() {
        return new DefaultStompContent(content().copy());
    }

    @Override
    public StompContent duplicate() {
        return new DefaultStompContent(content().duplicate());
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public StompContent retain() {
        content().retain();
        return this;
    }

    @Override
    public StompContent retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public StompContent touch() {
        content.toString();
        return this;
    }

    @Override
    public StompContent touch(Object hint) {
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
    public DecoderResult getDecoderResult() {
        return decoderResult;
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        this.decoderResult = result;
    }

    @Override
    public String toString() {
        return "DefaultStompContent{" +
            "decoderResult=" + decoderResult +
            '}';
    }
}

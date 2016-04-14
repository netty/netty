/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link HttpContent} implementation.
 */
public class DefaultHttpContent extends DefaultHttpObject implements HttpContent {

    private final ByteBuf content;

    /**
     * Creates a new instance with the specified chunk content.
     */
    public DefaultHttpContent(ByteBuf content) {
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
    public HttpContent copy() {
        return replace(content.copy());
    }

    @Override
    public HttpContent duplicate() {
        return replace(content.duplicate());
    }

    @Override
    public HttpContent retainedDuplicate() {
        return replace(content.retainedDuplicate());
    }

    @Override
    public HttpContent replace(ByteBuf content) {
        return new DefaultHttpContent(content);
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public HttpContent retain() {
        content.retain();
        return this;
    }

    @Override
    public HttpContent retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public HttpContent touch() {
        content.touch();
        return this;
    }

    @Override
    public HttpContent touch(Object hint) {
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
        return StringUtil.simpleClassName(this) +
               "(data: " + content() + ", decoderResult: " + decoderResult() + ')';
    }
}

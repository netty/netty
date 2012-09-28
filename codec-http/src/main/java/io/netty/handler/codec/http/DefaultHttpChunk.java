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

/**
 * The default {@link HttpChunk} implementation.
 */
public class DefaultHttpChunk extends DefaultHttpObject implements HttpChunk {

    private ByteBuf content;
    private boolean last;

    /**
     * Creates a new instance with the specified chunk content. If an empty
     * buffer is specified, this chunk becomes the 'end of content' marker.
     */
    public DefaultHttpChunk(ByteBuf content) {
        setContent(content);
    }

    @Override
    public ByteBuf getContent() {
        return content;
    }

    @Override
    public void setContent(ByteBuf content) {
        if (content == null) {
            throw new NullPointerException("content");
        }
        last = !content.readable();
        this.content = content;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());

        final boolean last = isLast();
        buf.append("(last: ");
        buf.append(last);
        if (!last) {
            buf.append(", size: ");
            buf.append(getContent().readableBytes());
        }

        buf.append(", decodeResult: ");
        buf.append(getDecoderResult());
        buf.append(')');

        return buf.toString();
    }
}

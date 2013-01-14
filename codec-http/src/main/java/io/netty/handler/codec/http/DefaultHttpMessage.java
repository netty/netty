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
import io.netty.buffer.Unpooled;

/**
 * Combination of {@link HttpHeader} and {@link LastHttpContent} which can be used to <i>combine</i> the headers and
 * the actual content. {@link HttpObjectAggregator} makes use of this.
 *
 */
public abstract class DefaultHttpMessage extends DefaultHttpHeader implements LastHttpContent {
    private ByteBuf content = Unpooled.EMPTY_BUFFER;

    public DefaultHttpMessage(HttpVersion version) {
        super(version);
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
        this.content = content;
    }
}

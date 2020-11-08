/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.internal.StringUtil;

/**
 * {@link DefaultHttp2TranslatedHttpContent} contains {@link HttpContent} and {@code streamId}
 * which are translated from {@link Http2DataFrame}
 */
public class DefaultHttp2TranslatedHttpContent extends Http2TranslatedHttpContent {

    /**
     * Creates a new instance with the specified chunk content and StreamId.
     */
    public DefaultHttp2TranslatedHttpContent(ByteBuf content, int streamId) {
        super(content, streamId);
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(streamId: " + streamId() + ", data: " + content() +
                ", decoderResult: " + decoderResult() + ')';
    }
}

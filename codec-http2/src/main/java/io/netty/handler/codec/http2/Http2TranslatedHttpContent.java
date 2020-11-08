/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;

public abstract class Http2TranslatedHttpContent extends DefaultHttpContent {

    private final int streamId;

    /***
     * Creates a new instance with the specified chunk content.
     * @param streamId Stream ID of HTTP/2 Data Frame
     */
    public Http2TranslatedHttpContent(ByteBuf content, int streamId) {
        super(content);
        this.streamId = streamId;
    }

    public int streamId() {
        return streamId;
    }
}

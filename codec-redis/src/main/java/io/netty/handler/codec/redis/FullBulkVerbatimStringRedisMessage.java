/*
 * Copyright 2021 The Netty Project
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

package io.netty.handler.codec.redis;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

@UnstableApi
public class FullBulkVerbatimStringRedisMessage extends FullBulkStringRedisMessage {

    /**
     * Creates a {@link FullBulkVerbatimStringRedisMessage} for the given {@code content}.
     * The first three bytes represent the format of the following string,
     * such as txt for plain text, mkd for markdown. The fourth byte is always `:`.
     * Then the real string follows.
     *
     * @param content the content include format, must not be {@code null}.
     */
    public FullBulkVerbatimStringRedisMessage(ByteBuf content) {
        super(content);
    }

    public String format() {
        return new String(ByteBufUtil.getBytes(content(), 0, 3));
    }

    public String realContent() {
        int length = super.content().writerIndex();
        return new String(ByteBufUtil.getBytes(content(), 4, length - 4));
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("format=")
                .append(format())
                .append(", content=")
                .append(realContent())
                .append(']').toString();
    }

}

/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.memcache.binary;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.memcache.AbstractMemcacheObjectEncoder;
import io.netty.util.CharsetUtil;

/**
 * A {@link MessageToByteEncoder} that encodes binary memache messages into bytes.
 */
public abstract class AbstractBinaryMemcacheEncoder
        <M extends BinaryMemcacheMessage<H>, H extends BinaryMemcacheMessageHeader>
    extends AbstractMemcacheObjectEncoder<M> {

    /**
     * Every binary memcache message has at least a 24 bytes header.
     */
    private static final int DEFAULT_BUFFER_SIZE = 24;

    @Override
    protected ByteBuf encodeMessage(ChannelHandlerContext ctx, M msg) {
        ByteBuf buf = ctx.alloc().buffer(DEFAULT_BUFFER_SIZE);

        encodeHeader(buf, msg.getHeader());
        encodeExtras(buf, msg.getExtras());
        encodeKey(buf, msg.getKey());

        return buf;
    }

    /**
     * Encode the extras.
     *
     * @param buf    the {@link ByteBuf} to write into.
     * @param extras the extras to encode.
     */
    private static void encodeExtras(ByteBuf buf, ByteBuf extras) {
        if (extras == null || !extras.isReadable()) {
            return;
        }

        buf.writeBytes(extras);
    }

    /**
     * Encode the key.
     *
     * @param buf the {@link ByteBuf} to write into.
     * @param key the key to encode.
     */
    private static void encodeKey(ByteBuf buf, String key) {
        if (key == null || key.isEmpty()) {
            return;
        }

        buf.writeBytes(key.getBytes(CharsetUtil.UTF_8));
    }

    /**
     * Encode the header.
     * <p/>
     * This methods needs to be implemented by a sub class because the header is different
     * for both requests and responses.
     *
     * @param buf    the {@link ByteBuf} to write into.
     * @param header the header to encode.
     */
    protected abstract void encodeHeader(ByteBuf buf, H header);

}

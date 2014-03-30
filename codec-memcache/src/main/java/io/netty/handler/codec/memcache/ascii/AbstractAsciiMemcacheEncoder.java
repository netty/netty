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
package io.netty.handler.codec.memcache.ascii;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.memcache.AbstractMemcacheObjectEncoder;
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheRetrieveResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheStatsResponse;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;
import java.util.List;


public abstract class AbstractAsciiMemcacheEncoder<M extends AsciiMemcacheMessage>
    extends AbstractMemcacheObjectEncoder<M> {

    protected static final byte[] CRLF = { 13, 10 };
    protected static final Charset CHARSET = CharsetUtil.UTF_8;
    protected static final byte[] END = "END".getBytes(CHARSET);

    private boolean needsFinalEnd = false;

    /**
     * Encode the underlying message.
     *
     * @param ctx the channel handler context.
     * @param msg the message to encode.
     * @return the encoded message.
     */
    protected abstract ByteBuf encodeMessage0(ChannelHandlerContext ctx, M msg);

    @Override
    protected ByteBuf encodeMessage(ChannelHandlerContext ctx, M msg) {
        needsFinalEnd = msg instanceof AsciiMemcacheRetrieveResponse;
        return encodeMessage0(ctx, msg).writeBytes(CRLF);
    }

    @Override
    protected void encodeContent(Object msg, List<Object> out) {
        super.encodeContent(msg, out);

        if (msg instanceof LastMemcacheContent) {
            out.add(CRLF);
            if (needsFinalEnd) {
                out.add(END);
                out.add(CRLF);
            }
        }
    }

}

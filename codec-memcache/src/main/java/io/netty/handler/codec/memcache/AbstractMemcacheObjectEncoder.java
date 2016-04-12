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
package io.netty.handler.codec.memcache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * A general purpose {@link AbstractMemcacheObjectEncoder} that encodes {@link MemcacheMessage}s.
 * <p/>
 * <p>Note that this class is designed to be extended, especially because both the binary and ascii protocol
 * require different treatment of their messages. Since the content chunk writing is the same for both, the encoder
 * abstracts this right away.</p>
 */
@UnstableApi
public abstract class AbstractMemcacheObjectEncoder<M extends MemcacheMessage> extends MessageToMessageEncoder<Object> {

    private boolean expectingMoreContent;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        if (msg instanceof MemcacheMessage) {
            if (expectingMoreContent) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }

            @SuppressWarnings({ "unchecked", "CastConflictsWithInstanceof" })
            final M m = (M) msg;
            out.add(encodeMessage(ctx, m));
        }

        if (msg instanceof MemcacheContent || msg instanceof ByteBuf || msg instanceof FileRegion) {
            int contentLength = contentLength(msg);
            if (contentLength > 0) {
                out.add(encodeAndRetain(msg));
            } else {
                out.add(Unpooled.EMPTY_BUFFER);
            }

            expectingMoreContent = !(msg instanceof LastMemcacheContent);
        }
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof MemcacheObject || msg instanceof ByteBuf || msg instanceof FileRegion;
    }

    /**
     * Take the given {@link MemcacheMessage} and encode it into a writable {@link ByteBuf}.
     *
     * @param ctx the channel handler context.
     * @param msg the message to encode.
     * @return the {@link ByteBuf} representation of the message.
     */
    protected abstract ByteBuf encodeMessage(ChannelHandlerContext ctx, M msg);

    /**
     * Determine the content length of the given object.
     *
     * @param msg the object to determine the length of.
     * @return the determined content length.
     */
    private static int contentLength(Object msg) {
        if (msg instanceof MemcacheContent) {
            return ((MemcacheContent) msg).content().readableBytes();
        }
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).readableBytes();
        }
        if (msg instanceof FileRegion) {
            return (int) ((FileRegion) msg).count();
        }
        throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
    }

    /**
     * Encode the content, depending on the object type.
     *
     * @param msg the object to encode.
     * @return the encoded object.
     */
    private static Object encodeAndRetain(Object msg) {
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).retain();
        }
        if (msg instanceof MemcacheContent) {
            return ((MemcacheContent) msg).content().retain();
        }
        if (msg instanceof FileRegion) {
            return ((FileRegion) msg).retain();
        }
        throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
    }

}

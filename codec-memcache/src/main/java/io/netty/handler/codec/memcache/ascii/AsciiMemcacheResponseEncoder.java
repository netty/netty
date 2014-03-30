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
package io.netty.handler.codec.memcache.ascii;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheArithmeticResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheDeleteResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheErrorResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheFlushResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheRetrieveResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheStatsResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheStoreResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheTouchResponse;
import io.netty.handler.codec.memcache.ascii.response.AsciiMemcacheVersionResponse;
import io.netty.util.internal.AppendableCharSequence;

import java.util.Map;

public class AsciiMemcacheResponseEncoder extends AbstractAsciiMemcacheEncoder<AsciiMemcacheResponse> {

    private final AppendableCharSequence seq = new AppendableCharSequence(1);

    @Override
    protected ByteBuf encodeMessage0(final ChannelHandlerContext ctx, final AsciiMemcacheResponse msg) {
        ByteBuf buffer = ctx.alloc().buffer();

        if (msg instanceof AsciiMemcacheStoreResponse) {
            encodeStoreResponse(buffer, (AsciiMemcacheStoreResponse) msg);
        } else if (msg instanceof AsciiMemcacheErrorResponse) {
            encodeErrorResponse(buffer, (AsciiMemcacheErrorResponse) msg);
        } else if (msg instanceof AsciiMemcacheRetrieveResponse) {
            encodeRetrieveResponse(buffer, (AsciiMemcacheRetrieveResponse) msg);
        } else if (msg instanceof AsciiMemcacheDeleteResponse) {
            encodeDeleteResponse(buffer, (AsciiMemcacheDeleteResponse) msg);
        } else if (msg instanceof AsciiMemcacheArithmeticResponse) {
            encodeArithmeticResponse(buffer, (AsciiMemcacheArithmeticResponse) msg);
        } else if (msg instanceof AsciiMemcacheTouchResponse) {
            encodeTouchResponse(buffer, (AsciiMemcacheTouchResponse) msg);
        } else if (msg instanceof AsciiMemcacheVersionResponse) {
            encodeVersionResponse(buffer, (AsciiMemcacheVersionResponse) msg);
        } else if (msg instanceof AsciiMemcacheFlushResponse) {
            encodeFlushResponse(buffer);
        } else if (msg instanceof AsciiMemcacheStatsResponse) {
            encodeStatsResponse(buffer, (AsciiMemcacheStatsResponse) msg);
        } else {
            throw new IllegalStateException("Got a response message I cannot encode: "
                + msg.getClass().getCanonicalName());
        }

        return buffer;
    }

    private void encodeStoreResponse(final ByteBuf buffer, final AsciiMemcacheStoreResponse msg) {
        buffer.writeBytes(msg.getResponse().getValue().getBytes(CHARSET));
    }

    private void encodeErrorResponse(final ByteBuf buffer, final AsciiMemcacheErrorResponse msg) {
        buffer.writeBytes(msg.getResponse().getValue().getBytes(CHARSET));

        String message = msg.getMessage();
        if (message != null && !message.isEmpty()) {
            message  = " " + message;
            buffer.writeBytes(message.getBytes(CHARSET));
        }
    }

    private void encodeRetrieveResponse(final ByteBuf buffer, final AsciiMemcacheRetrieveResponse msg) {
        AppendableCharSequence seq = this.seq;
        seq.reset();

        seq.append("VALUE ");
        seq.append(msg.getKey());
        seq.append(" ");
        seq.append(String.valueOf(msg.getFlags()));
        seq.append(" ");
        seq.append(String.valueOf(msg.getLength()));

        if (msg.getCas() > 0) {
            seq.append(" ");
            seq.append(String.valueOf(msg.getCas()));
        }

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }

    private void encodeDeleteResponse(final ByteBuf buffer, final AsciiMemcacheDeleteResponse msg) {
        buffer.writeBytes(msg.getResponse().getValue().getBytes(CHARSET));
    }

    private void encodeArithmeticResponse(final ByteBuf buffer, final AsciiMemcacheArithmeticResponse msg) {
        if (msg.getFound()) {
            buffer.writeBytes(String.valueOf(msg.getValue()).getBytes(CHARSET));
        } else {
            buffer.writeBytes("NOT_FOUND".getBytes(CHARSET));
        }
    }

    private void encodeTouchResponse(final ByteBuf buffer, final AsciiMemcacheTouchResponse msg) {
        buffer.writeBytes(msg.getResponse().getValue().getBytes(CHARSET));
    }

    private void encodeVersionResponse(final ByteBuf buffer, final AsciiMemcacheVersionResponse msg) {
        AppendableCharSequence seq = this.seq;
        seq.reset();

        seq.append("VERSION ");
        seq.append(msg.getVersion());

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }

    private void encodeFlushResponse(final ByteBuf buffer) {
        buffer.writeBytes("OK".getBytes(CHARSET));
    }

    private void encodeStatsResponse(final ByteBuf buffer, final AsciiMemcacheStatsResponse msg) {
        AppendableCharSequence seq = this.seq;

        for (Map.Entry<String, String> entry : msg.getStats().entrySet()) {
            seq.reset();
            seq.append("STAT ");
            seq.append(entry.getKey());
            seq.append(" ");
            seq.append(entry.getValue());
            buffer.writeBytes(seq.toString().getBytes(CHARSET));
            buffer.writeBytes(CRLF);
        }

        buffer.writeBytes(END);
    }


}

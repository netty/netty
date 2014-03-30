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
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheArithmeticRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheDeleteRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheFlushRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheQuitRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheRetrieveRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheStatsRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheStoreRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheTouchRequest;
import io.netty.handler.codec.memcache.ascii.request.AsciiMemcacheVersionRequest;
import io.netty.util.internal.AppendableCharSequence;


public class AsciiMemcacheRequestEncoder extends AbstractAsciiMemcacheEncoder<AsciiMemcacheRequest> {

    private final AppendableCharSequence seq = new AppendableCharSequence(1);

    @Override
    protected ByteBuf encodeMessage0(final ChannelHandlerContext ctx, final AsciiMemcacheRequest msg) {
        ByteBuf buffer = ctx.alloc().buffer();
        this.seq.reset();

        if (msg instanceof AsciiMemcacheStoreRequest) {
            encodeStoreRequest(buffer, (AsciiMemcacheStoreRequest) msg);
        } else if (msg instanceof AsciiMemcacheRetrieveRequest) {
            encodeRetrieveRequest(buffer, (AsciiMemcacheRetrieveRequest) msg);
        } else if (msg instanceof AsciiMemcacheDeleteRequest) {
            encodeDeleteRequest(buffer, (AsciiMemcacheDeleteRequest) msg);
        } else if (msg instanceof AsciiMemcacheArithmeticRequest) {
            encodeArithmeticRequest(buffer, (AsciiMemcacheArithmeticRequest) msg);
        } else if (msg instanceof AsciiMemcacheTouchRequest) {
            encodeTouchRequest(buffer, (AsciiMemcacheTouchRequest) msg);
        } else if (msg instanceof AsciiMemcacheVersionRequest) {
            encodeVersionRequest(buffer);
        } else if (msg instanceof AsciiMemcacheFlushRequest) {
            encodeFlushRequest(buffer, (AsciiMemcacheFlushRequest) msg);
        } else if (msg instanceof AsciiMemcacheQuitRequest) {
            encodeQuitRequest(buffer);
        } else if (msg instanceof AsciiMemcacheStatsRequest) {
            encodeStatsRequest(buffer, (AsciiMemcacheStatsRequest) msg);
        }  else {
            throw new IllegalStateException("Got a request message I cannot encode: "
                + msg.getClass().getCanonicalName());
        }

        return buffer;
    }

    private void encodeStoreRequest(final ByteBuf buffer, final AsciiMemcacheStoreRequest msg) {
        AppendableCharSequence seq = this.seq;

        seq.append(msg.getCmd().getValue());
        seq.append(" ");
        seq.append(msg.getKey());
        seq.append(" ");
        seq.append(String.valueOf(msg.getFlags()));
        seq.append(" ");
        seq.append(String.valueOf(msg.getExpiration()));
        seq.append(" ");
        seq.append(String.valueOf(msg.getLength()));

        if (msg.getCmd() == AsciiMemcacheStoreRequest.StorageCommand.CAS) {
            seq.append(" " + String.valueOf(msg.getCas()));
        }

        if (msg.getNoreply()) {
            seq.append(" noreply");
        }

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }

    private void encodeRetrieveRequest(final ByteBuf buffer, final AsciiMemcacheRetrieveRequest msg) {
        AppendableCharSequence seq = this.seq;

        seq.append(msg.getCommand().getValue());
        seq.append(" ");

        String[] keys = msg.getKeys();
        int length = keys.length;
        for (int i = 0; i < length; i++) {
            seq.append(keys[i]);
            if (i+1 < length) {
                seq.append(" ");
            }
        }

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }

    private void encodeDeleteRequest(final ByteBuf buffer, final AsciiMemcacheDeleteRequest msg) {
        AppendableCharSequence seq = this.seq;

        seq.append("delete ");
        seq.append(msg.getKey());
        if (msg.getNoreply()) {
            seq.append(" noreply");
        }

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }

    private void encodeArithmeticRequest(final ByteBuf buffer, final AsciiMemcacheArithmeticRequest msg) {
        AppendableCharSequence seq = this.seq;

        seq.append(msg.getCommand().getValue());
        seq.append(" ");
        seq.append(msg.getKey());
        seq.append(" ");
        seq.append(String.valueOf(msg.getAmount()));

        if (msg.getNoreply()) {
            seq.append(" noreply");
        }

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }

    private void encodeTouchRequest(final ByteBuf buffer, final AsciiMemcacheTouchRequest msg) {
        AppendableCharSequence seq = this.seq;

        seq.append("touch ");
        seq.append(msg.getKey());
        seq.append(" ");
        seq.append(String.valueOf(msg.getExpiration()));

        if (msg.getNoreply()) {
            seq.append(" noreply");
        }

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }

    private void encodeVersionRequest(final ByteBuf buffer) {
        buffer.writeBytes("version".getBytes(CHARSET));
    }

    private void encodeFlushRequest(final ByteBuf buffer, final AsciiMemcacheFlushRequest msg) {
        AppendableCharSequence seq = this.seq;

        seq.append("flush_all");

        if (msg.getNoreply()) {
            seq.append(" noreply");
        }

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }

    private void encodeQuitRequest(final ByteBuf buffer) {
        buffer.writeBytes("quit".getBytes(CHARSET));
    }

    private void encodeStatsRequest(final ByteBuf buffer, final AsciiMemcacheStatsRequest msg) {
        AppendableCharSequence seq = this.seq;

        seq.append("stats ");

        String[] stats = msg.getStats();
        int length = stats.length;
        for (int i = 0; i < length; i++) {
            seq.append(stats[i]);
            if (i+1 < length) {
                seq.append(" ");
            }
        }

        buffer.writeBytes(seq.toString().getBytes(CHARSET));
    }
}

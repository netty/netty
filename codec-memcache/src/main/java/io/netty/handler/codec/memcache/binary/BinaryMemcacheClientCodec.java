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
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.util.internal.UnstableApi;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The client codec that combines the proper encoder and decoder.
 * <p/>
 * Use this codec if you want to implement a memcache client that speaks the binary protocol. It
 * combines both the {@link BinaryMemcacheResponseDecoder} and the {@link BinaryMemcacheRequestEncoder}.
 * <p/>
 * Optionally, it counts the number of outstanding responses and raises an exception if - on connection
 * close - the list is not 0 (this is turned off by default). You can also define a chunk size for the
 * content, which defaults to 8192. This chunk size is the maximum, so if smaller chunks arrive they
 * will be passed up the pipeline and not queued up to the chunk size.
 */
@UnstableApi
public final class BinaryMemcacheClientCodec extends
        CombinedChannelDuplexHandler<BinaryMemcacheResponseDecoder, BinaryMemcacheRequestEncoder> {

    private final boolean failOnMissingResponse;
    private final AtomicLong requestResponseCounter = new AtomicLong();

    /**
     * Create a new {@link BinaryMemcacheClientCodec} with the default settings applied.
     */
    public BinaryMemcacheClientCodec() {
        this(AbstractBinaryMemcacheDecoder.DEFAULT_MAX_CHUNK_SIZE);
    }

    /**
     * Create a new {@link BinaryMemcacheClientCodec} and set a custom chunk size.
     *
     * @param decodeChunkSize the maximum chunk size.
     */
    public BinaryMemcacheClientCodec(int decodeChunkSize) {
        this(decodeChunkSize, false);
    }

    /**
     * Create a new {@link BinaryMemcacheClientCodec} with custom settings.
     *
     * @param decodeChunkSize       the maximum chunk size.
     * @param failOnMissingResponse report if after close there are outstanding requests.
     */
    public BinaryMemcacheClientCodec(int decodeChunkSize, boolean failOnMissingResponse) {
        this.failOnMissingResponse = failOnMissingResponse;
        init(new Decoder(decodeChunkSize), new Encoder());
    }

    private final class Encoder extends BinaryMemcacheRequestEncoder {

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);

            if (failOnMissingResponse && msg instanceof LastMemcacheContent) {
                requestResponseCounter.incrementAndGet();
            }
        }
    }

    private final class Decoder extends BinaryMemcacheResponseDecoder {

        Decoder(int chunkSize) {
            super(chunkSize);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            int oldSize = out.size();
            super.decode(ctx, in, out);

            if (failOnMissingResponse) {
                final int size = out.size();
                for (int i = oldSize; i < size; i ++) {
                    Object msg = out.get(i);
                    if (msg instanceof LastMemcacheContent) {
                        requestResponseCounter.decrementAndGet();
                    }
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);

            if (failOnMissingResponse) {
                long missingResponses = requestResponseCounter.get();
                if (missingResponses > 0) {
                    ctx.fireExceptionCaught(new PrematureChannelClosureException(
                        "channel gone inactive with " + missingResponses +
                            " missing response(s)"));
                }
            }
        }
    }
}

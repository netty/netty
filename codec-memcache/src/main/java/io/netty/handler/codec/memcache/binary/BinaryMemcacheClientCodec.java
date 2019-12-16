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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.UnstableApi;

import java.net.SocketAddress;
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

        private ChannelHandlerContext context;

        Decoder(int chunkSize) {
            super(chunkSize);
        }

        @Override
        protected void handlerAdded0(final ChannelHandlerContext ctx) {
            context = new ChannelHandlerContext() {
                @Override
                public Channel channel() {
                    return ctx.channel();
                }

                public EventExecutor executor() {
                    return ctx.executor();
                }

                public String name() {
                    return ctx.name();
                }

                public ChannelHandler handler() {
                    return ctx.handler();
                }

                public boolean isRemoved() {
                    return ctx.isRemoved();
                }

                public ChannelHandlerContext fireChannelRegistered() {
                    ctx.fireChannelRegistered();
                    return this;
                }

                public ChannelHandlerContext fireChannelUnregistered() {
                    ctx.fireChannelUnregistered();
                    return this;
                }

                public ChannelHandlerContext fireChannelActive() {
                    ctx.fireChannelActive();
                    return this;
                }

                public ChannelHandlerContext fireChannelInactive() {
                    ctx.fireChannelInactive();
                    return this;
                }

                public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
                    ctx.fireExceptionCaught(cause);
                    return this;
                }

                public ChannelHandlerContext fireUserEventTriggered(Object evt) {
                    ctx.fireUserEventTriggered(evt);
                    return this;
                }

                public ChannelHandlerContext fireChannelRead(Object msg) {
                    if (failOnMissingResponse && msg instanceof LastMemcacheContent) {
                        requestResponseCounter.decrementAndGet();
                    }
                    ctx.fireChannelRead(msg);
                    return this;
                }

                public ChannelHandlerContext fireChannelReadComplete() {
                    ctx.fireChannelReadComplete();
                    return this;
                }

                public ChannelHandlerContext fireChannelWritabilityChanged() {
                    ctx.fireChannelWritabilityChanged();
                    return this;
                }

                public ChannelHandlerContext read() {
                    ctx.read();
                    return this;
                }

                public ChannelHandlerContext flush() {
                    ctx.flush();
                    return this;
                }

                public ChannelPipeline pipeline() {
                    return ctx.pipeline();
                }

                public ByteBufAllocator alloc() {
                    return ctx.alloc();
                }

                @Deprecated
                public <T> Attribute<T> attr(AttributeKey<T> key) {
                    return ctx.attr(key);
                }

                @Deprecated
                public <T> boolean hasAttr(AttributeKey<T> key) {
                    return ctx.hasAttr(key);
                }

                public ChannelFuture bind(SocketAddress localAddress) {
                    return ctx.bind(localAddress);
                }

                public ChannelFuture connect(SocketAddress remoteAddress) {
                    return ctx.connect(remoteAddress);
                }

                public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
                    return ctx.connect(remoteAddress, localAddress);
                }

                public ChannelFuture disconnect() {
                    return ctx.disconnect();
                }

                public ChannelFuture close() {
                    return ctx.close();
                }

                public ChannelFuture deregister() {
                    return ctx.deregister();
                }

                @Override
                public ChannelFuture register() {
                    return ctx.register();
                }

                @Override
                public ChannelFuture register(ChannelPromise promise) {
                    return ctx.register(promise);
                }

                public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
                    return ctx.bind(localAddress, promise);
                }

                public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
                    return ctx.connect(remoteAddress, promise);
                }

                public ChannelFuture connect(
                        SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                    return ctx.connect(remoteAddress, localAddress, promise);
                }

                public ChannelFuture disconnect(ChannelPromise promise) {
                    return ctx.disconnect(promise);
                }

                public ChannelFuture close(ChannelPromise promise) {
                    return ctx.close(promise);
                }

                public ChannelFuture deregister(ChannelPromise promise) {
                    return ctx.deregister(promise);
                }

                public ChannelFuture write(Object msg) {
                    return ctx.write(msg);
                }

                public ChannelFuture write(Object msg, ChannelPromise promise) {
                    return ctx.write(msg, promise);
                }

                public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
                    return ctx.writeAndFlush(msg, promise);
                }

                public ChannelFuture writeAndFlush(Object msg) {
                    return ctx.writeAndFlush(msg);
                }

                public ChannelPromise newPromise() {
                    return ctx.newPromise();
                }

                public ChannelProgressivePromise newProgressivePromise() {
                    return ctx.newProgressivePromise();
                }

                public ChannelFuture newSucceededFuture() {
                    return ctx.newSucceededFuture();
                }

                public ChannelFuture newFailedFuture(Throwable cause) {
                    return ctx.newFailedFuture(cause);
                }

                public ChannelPromise voidPromise() {
                    return ctx.voidPromise();
                }
            };
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            super.decode(context, in);
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

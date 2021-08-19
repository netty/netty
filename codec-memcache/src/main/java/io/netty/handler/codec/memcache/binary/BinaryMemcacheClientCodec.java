/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
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

                @Override
                public EventExecutor executor() {
                    return ctx.executor();
                }

                @Override
                public String name() {
                    return ctx.name();
                }

                @Override
                public ChannelHandler handler() {
                    return ctx.handler();
                }

                @Override
                public boolean isRemoved() {
                    return ctx.isRemoved();
                }

                @Override
                public ChannelHandlerContext fireChannelRegistered() {
                    ctx.fireChannelRegistered();
                    return this;
                }

                @Override
                public ChannelHandlerContext fireChannelUnregistered() {
                    ctx.fireChannelUnregistered();
                    return this;
                }

                @Override
                public ChannelHandlerContext fireChannelActive() {
                    ctx.fireChannelActive();
                    return this;
                }

                @Override
                public ChannelHandlerContext fireChannelInactive() {
                    ctx.fireChannelInactive();
                    return this;
                }

                @Override
                public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
                    ctx.fireExceptionCaught(cause);
                    return this;
                }

                @Override
                public ChannelHandlerContext fireUserEventTriggered(Object evt) {
                    ctx.fireUserEventTriggered(evt);
                    return this;
                }

                @Override
                public ChannelHandlerContext fireChannelRead(Object msg) {
                    if (failOnMissingResponse && msg instanceof LastMemcacheContent) {
                        requestResponseCounter.decrementAndGet();
                    }
                    ctx.fireChannelRead(msg);
                    return this;
                }

                @Override
                public ChannelHandlerContext fireChannelReadComplete() {
                    ctx.fireChannelReadComplete();
                    return this;
                }

                @Override
                public ChannelHandlerContext fireChannelWritabilityChanged() {
                    ctx.fireChannelWritabilityChanged();
                    return this;
                }

                @Override
                public ChannelHandlerContext read() {
                    ctx.read();
                    return this;
                }

                @Override
                public ChannelHandlerContext flush() {
                    ctx.flush();
                    return this;
                }

                @Override
                public ChannelPipeline pipeline() {
                    return ctx.pipeline();
                }

                @Override
                public ByteBufAllocator alloc() {
                    return ctx.alloc();
                }

                @Override
                @Deprecated
                public <T> Attribute<T> attr(AttributeKey<T> key) {
                    return ctx.attr(key);
                }

                @Override
                @Deprecated
                public <T> boolean hasAttr(AttributeKey<T> key) {
                    return ctx.hasAttr(key);
                }

                @Override
                public Future<Void> bind(SocketAddress localAddress) {
                    return ctx.bind(localAddress);
                }

                @Override
                public Future<Void> connect(SocketAddress remoteAddress) {
                    return ctx.connect(remoteAddress);
                }

                @Override
                public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
                    return ctx.connect(remoteAddress, localAddress);
                }

                @Override
                public Future<Void> disconnect() {
                    return ctx.disconnect();
                }

                @Override
                public Future<Void> close() {
                    return ctx.close();
                }

                @Override
                public Future<Void> deregister() {
                    return ctx.deregister();
                }

                @Override
                public Future<Void> register() {
                    return ctx.register();
                }

                @Override
                public ChannelHandlerContext register(Promise<Void> promise) {
                    ctx.register(promise);
                    return this;
                }

                @Override
                public ChannelHandlerContext bind(SocketAddress localAddress, Promise<Void> promise) {
                    ctx.bind(localAddress, promise);
                    return this;
                }

                @Override
                public ChannelHandlerContext connect(SocketAddress remoteAddress, Promise<Void> promise) {
                    ctx.connect(remoteAddress, promise);
                    return this;
                }

                @Override
                public ChannelHandlerContext connect(
                        SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
                    ctx.connect(remoteAddress, localAddress, promise);
                    return this;
                }

                @Override
                public ChannelHandlerContext disconnect(Promise<Void> promise) {
                    ctx.disconnect(promise);
                    return this;
                }

                @Override
                public ChannelHandlerContext close(Promise<Void> promise) {
                    ctx.close(promise);
                    return this;
                }

                @Override
                public ChannelHandlerContext deregister(Promise<Void> promise) {
                    ctx.deregister(promise);
                    return this;
                }

                @Override
                public Future<Void> write(Object msg) {
                    return ctx.write(msg);
                }

                @Override
                public ChannelHandlerContext write(Object msg, Promise<Void> promise) {
                    ctx.write(msg, promise);
                    return this;
                }

                @Override
                public ChannelHandlerContext writeAndFlush(Object msg, Promise<Void> promise) {
                    ctx.writeAndFlush(msg, promise);
                    return this;
                }

                @Override
                public Future<Void> writeAndFlush(Object msg) {
                    return ctx.writeAndFlush(msg);
                }

                @Override
                public Promise<Void> newPromise() {
                    return ctx.newPromise();
                }

                @Override
                public Future<Void> newSucceededFuture() {
                    return ctx.newSucceededFuture();
                }

                @Override
                public Future<Void> newFailedFuture(Throwable cause) {
                    return ctx.newFailedFuture(cause);
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

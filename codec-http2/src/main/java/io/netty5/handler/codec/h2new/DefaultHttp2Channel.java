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

package io.netty.handler.codec.h2new;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

final class DefaultHttp2Channel implements Http2Channel {
    private final Channel delegate;
    private final ChannelFlowControlledBytesDistributor distributor;
    private final boolean isServer;

    // accessed from the eventloop
    private int nextStreamId;

    DefaultHttp2Channel(Channel delegate, ChannelFlowControlledBytesDistributor distributor, boolean isServer) {
        this.delegate = delegate;
        this.distributor = distributor;
        // https://httpwg.org/specs/rfc7540.html#StreamIdentifiers
        // Streams initiated by a client MUST use odd-numbered stream identifiers; those initiated by the server MUST
        // use even-numbered stream identifiers.
        nextStreamId = isServer ? 0 : -1;
        this.isServer = isServer;
    }

    @Override
    public Future<Http2StreamChannel> createStream(ChannelHandler handler) {
        final Promise<Http2StreamChannel> promise = executor().newPromise();
        if (delegate.executor().inEventLoop()) {
            createStream0(handler, promise);
        } else {
            delegate.executor().execute(() -> createStream0(handler, promise));
        }
        return promise;
    }

    private void createStream0(ChannelHandler handler, Promise<Http2StreamChannel> promise) {
        nextStreamId += 2;
        DefaultHttp2StreamChannel stream = new DefaultHttp2StreamChannel(this, isServer, nextStreamId);
        // Send the stream on the pipeline for it to be configured by existing handlers, before we add the user provided
        // handler.
        pipeline().write(stream);
        stream.pipeline().addLast(handler);
        stream.register().addListener(future -> {
            if (future.isSuccess()) {
                promise.setSuccess(stream);
            } else {
                if (future.isCancelled()) {
                    promise.cancel(false);
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });
    }

    @Override
    public Http2StreamChannelBootstrap newStreamBootstrap() {
        return new DefaultHttp2StreamChannelBootstrap(this);
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return delegate.attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return delegate.hasAttr(key);
    }

    @Override
    public ChannelId id() {
        return delegate.id();
    }

    @Override
    public EventLoop executor() {
        return delegate.executor();
    }

    @Override
    public Channel parent() {
        return delegate.parent();
    }

    @Override
    public ChannelConfig config() {
        return delegate.config();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public boolean isRegistered() {
        return delegate.isRegistered();
    }

    @Override
    public boolean isActive() {
        return delegate.isActive();
    }

    @Override
    public ChannelMetadata metadata() {
        return delegate.metadata();
    }

    @Override
    public SocketAddress localAddress() {
        return delegate.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return delegate.remoteAddress();
    }

    @Override
    public Future<Void> closeFuture() {
        return delegate.closeFuture();
    }

    @Override
    public boolean isWritable() {
        return delegate.isWritable();
    }

    @Override
    public long bytesBeforeUnwritable() {
        return delegate.bytesBeforeUnwritable();
    }

    @Override
    public long bytesBeforeWritable() {
        return delegate.bytesBeforeWritable();
    }

    @Override
    public Unsafe unsafe() {
        return delegate.unsafe();
    }

    @Override
    public ChannelPipeline pipeline() {
        return delegate.pipeline();
    }

    @Override
    public ByteBufAllocator alloc() {
        return delegate.alloc();
    }

    @Override
    public Channel read() {
        return delegate.read();
    }

    @Override
    public Channel flush() {
        return delegate.flush();
    }

    @Override
    public Future<Void> bind(SocketAddress localAddress) {
        return delegate.bind(localAddress);
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress) {
        return delegate.connect(remoteAddress);
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return delegate.connect(remoteAddress, localAddress);
    }

    @Override
    public Future<Void> disconnect() {
        return delegate.disconnect();
    }

    @Override
    public Future<Void> close() {
        return delegate.close();
    }

    @Override
    public Future<Void> register() {
        return delegate.register();
    }

    @Override
    public Future<Void> deregister() {
        return delegate.deregister();
    }

    @Override
    public Future<Void> write(Object msg) {
        return delegate.write(msg);
    }

    @Override
    public Future<Void> writeAndFlush(Object msg) {
        return delegate.writeAndFlush(msg);
    }

    @Override
    public Promise<Void> newPromise() {
        return delegate.newPromise();
    }

    @Override
    public Future<Void> newSucceededFuture() {
        return delegate.newSucceededFuture();
    }

    @Override
    public Future<Void> newFailedFuture(Throwable cause) {
        return delegate.newFailedFuture(cause);
    }

    @Override
    public int compareTo(Channel o) {
        return delegate.compareTo(o);
    }
}

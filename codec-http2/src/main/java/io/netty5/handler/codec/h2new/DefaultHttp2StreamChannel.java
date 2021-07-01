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
package io.netty5.handler.codec.h2new;

import io.netty.buffer.ByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelId;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultChannelConfig;
import io.netty5.channel.DefaultChannelPipeline;
import io.netty5.channel.EventLoop;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.handler.codec.h2new.Http2ControlStreamInitializer.ControlStream;
import io.netty5.util.DefaultAttributeMap;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.ObjectPool;
import io.netty5.util.internal.ObjectPool.Handle;
import io.netty5.util.internal.RecyclableArrayList;
import io.netty5.util.internal.StringUtil;

import java.net.SocketAddress;

import static io.netty5.handler.codec.h2new.Http2ControlStreamInitializer.CONTROL_STREAM_ATTRIBUTE_KEY;

final class DefaultHttp2StreamChannel extends DefaultAttributeMap implements Http2StreamChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private final DefaultHttp2Channel parent;
    private final boolean isServer;
    private final int streamId;
    private final ChannelConfig config;
    private final Http2StreamChannelUnsafe unsafe;
    private final ChannelId channelId;
    private final Promise<Void> closePromise;
    private final ChannelPipeline pipeline;
    private final ControlStream controlStream;

    DefaultHttp2StreamChannel(DefaultHttp2Channel parent, boolean isServer, int streamId) {
        this.parent = parent;
        this.isServer = isServer;
        this.streamId = streamId;
        unsafe = new Http2StreamChannelUnsafe();
        channelId = new Http2StreamChannelId(parent.id(), streamId);
        closePromise = parent.newPromise();
        controlStream = parent.attr(CONTROL_STREAM_ATTRIBUTE_KEY).get();
        if (streamId != 0 && controlStream == null) {
            throw new IllegalArgumentException("Control stream attribute " + CONTROL_STREAM_ATTRIBUTE_KEY.name() +
                    " not set on the channel: " + parent);
        }
        config = new DefaultChannelConfig(this);
        pipeline = new DefaultChannelPipeline(this);
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public ChannelId id() {
        return channelId;
    }

    @Override
    public EventLoop executor() {
        return parent.executor();
    }

    @Override
    public Http2Channel parent() {
        return parent;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return !closePromise.isDone();
    }

    @Override
    public boolean isRegistered() {
        return false;
    }

    @Override
    public boolean isActive() {
        return isOpen();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SocketAddress localAddress() {
        return parent.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return parent.remoteAddress();
    }

    @Override
    public Future<Void> closeFuture() {
        return closePromise.asFuture();
    }

    @Override
    public boolean isWritable() {
        // TODO: Fix this to allow calls from outside EL
        if (!parent.executor().inEventLoop()) {
            throw new IllegalArgumentException("Invalid call to isWritable(), should be on eventloop");
        }
        return parent().isWritable() && unsafe.isWritable();
    }

    @Override
    public long bytesBeforeUnwritable() {
        // TODO: Fix this to allow calls from outside EL
        if (!parent.executor().inEventLoop()) {
            throw new IllegalArgumentException("Invalid call to bytesBeforeUnwritable(), should be on eventloop");
        }
        return unsafe.bytesBeforeUnwritable();
    }

    @Override
    public long bytesBeforeWritable() {
        // TODO: Fix this to allow calls from outside EL
        if (!parent.executor().inEventLoop()) {
            throw new IllegalArgumentException("Invalid call to bytesBeforeWritable(), should be on eventloop");
        }
        return unsafe.bytesBeforeWritable();
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return parent.alloc();
    }

    @Override
    public Future<Void> bind(SocketAddress localAddress) {
        return pipeline.bind(localAddress);
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress) {
        return pipeline.connect(remoteAddress);
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline.connect(remoteAddress, localAddress);
    }

    @Override
    public Future<Void> disconnect() {
        return pipeline.disconnect();
    }

    @Override
    public Future<Void> close() {
        return pipeline.close();
    }

    @Override
    public Future<Void> register() {
        return pipeline.register();
    }

    @Override
    public Future<Void> deregister() {
        return pipeline.deregister();
    }

    @Override
    public Channel read() {
        pipeline.read();
        return this;
    }

    @Override
    public Future<Void> write(Object msg) {
        return pipeline.write(msg);
    }

    @Override
    public Channel flush() {
        pipeline.flush();
        return this;
    }

    @Override
    public Future<Void> writeAndFlush(Object msg) {
        return pipeline.writeAndFlush(msg);
    }

    @Override
    public Promise<Void> newPromise() {
        return pipeline.newPromise();
    }

    @Override
    public Future<Void> newSucceededFuture() {
        return pipeline.newSucceededFuture();
    }

    @Override
    public Future<Void> newFailedFuture(Throwable cause) {
        return pipeline.newFailedFuture(cause);
    }

    @Override
    public int compareTo(Channel other) {
        return this == other ? 0 : id().compareTo(other.id());
    }

    void frameRead(Http2Frame frame) {
        assert parent.executor().inEventLoop();

        unsafe.frameRead(frame);
    }

    private final class Http2StreamChannelUnsafe implements Unsafe {
        private final RecyclableArrayList inboundBuffer = RecyclableArrayList.newInstance(4);
        private final RecyclableArrayList outboundBuffer = RecyclableArrayList.newInstance();
        private int unflushedBytes;
        private boolean unwritable;

        private byte state;
        @Override
        public RecvBufferAllocator.Handle recvBufAllocHandle() {
            throw new UnsupportedOperationException("Buffer allocations not supported by HTTP/2 streams.");
        }

        @Override
        public SocketAddress localAddress() {
            return parent().unsafe().localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return parent().unsafe().remoteAddress();
        }

        @Override
        public void register(Promise<Void> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            if (UnsafeState.isRegistered(state)) {
                promise.setFailure(new UnsupportedOperationException("Re-register not supported for HTTP/2 streams"));
                return;
            }

            state = UnsafeState.setRegisterStarted(state);

            if (streamId == 0 || isPeerInitiatedStream() || controlStream.tryReserveActiveStream()) {
                onStreamReserved(promise);
            } else {
                controlStream.reserveActiveStreamWhenAvailable().addListener(future -> {
                    if (future.isSuccess()) {
                        onStreamReserved(promise);
                    } else {
                        if (future.isCancelled()) {
                            promise.cancel();
                        } else {
                            promise.setFailure(future.cause());
                        }
                    }
                });
            }
        }

        private boolean isPeerInitiatedStream() {
            return (isServer && streamId % 2 != 0) || (!isServer && streamId % 2 == 0);
        }

        private void onStreamReserved(Promise<Void> promise) {
            state = UnsafeState.setReserved(state);
            promise.setSuccess(null);
            pipeline().fireChannelRegistered();
            if (isActive()) {
                pipeline().fireChannelActive();
                if (config().isAutoRead()) {
                    read();
                }
            }
        }

        @Override
        public void bind(SocketAddress localAddress, Promise<Void> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            promise.setFailure(new UnsupportedOperationException("Bind not supported for HTTP/2 streams."));
        }

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            promise.setFailure(new UnsupportedOperationException("Connect not supported for HTTP/2 streams."));
        }

        @Override
        public void disconnect(Promise<Void> promise) {
            close(promise);
        }

        @Override
        public void close(Promise<Void> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            close0();
            promise.setSuccess(null);
        }

        @Override
        public void closeForcibly() {
            close0();
        }

        private void close0() {
            if (UnsafeState.isReserved(state) && streamId > 0) {
                controlStream.reservedActiveStreamClosed();
            }
        }

        @Override
        public void deregister(Promise<Void> promise) {
        }

        @Override
        public void beginRead() {
            if (!isActive()) {
                return;
            }

            if (UnsafeState.isReadIdle(state)) {
                state = UnsafeState.setReadInProgress(state);
                doRead();
            } else {
                state = UnsafeState.setReadRequested(state);
            }
        }

        void frameRead(Http2Frame frame) {
            if (!UnsafeState.isReadIdle(state)) {
                sendFrameRead(frame);
                readCompleted();
            } else {
                inboundBuffer.add(frame);
            }
        }

        private void doRead() {
            state = UnsafeState.setReadInProgress(state);
            while (!inboundBuffer.isEmpty()) {
                for (int i = 0, size = inboundBuffer.size(); i < size; i++) {
                    Object obj = inboundBuffer.get(i);
                    Http2Frame frame = (Http2Frame) obj;
                    sendFrameRead(frame);
                }
            }
            readCompleted();
        }

        private void sendFrameRead(Http2Frame frame) {
            pipeline().fireChannelRead(frame);
        }

        private void readCompleted() {
            if (!UnsafeState.isReadRequested(state)) {
                state = UnsafeState.setReadIdle(state);
            }
            pipeline().fireChannelReadComplete();
            if (config().isAutoRead()) {
                read();
            }
        }

        @Override
        public void write(Object msg, Promise<Void> promise) {
            if (!(msg instanceof Buffer)) {
                ReferenceCountUtil.release(msg);
                promise.setFailure(new IllegalArgumentException("HTTP/2 streams only support " +
                        StringUtil.simpleClassName(Buffer.class) + ", but found: " +
                        StringUtil.simpleClassName(msg.getClass())));
                return;
            }

            final Buffer buffer = (Buffer) msg;
            unflushedBytes += buffer.readableBytes();
            outboundBuffer.add(Entry.newInstance(buffer, promise));
        }

        @Override
        public void flush() {
            for (int i = 0, size = outboundBuffer.size(); i < size; i++) {
                Object entryObj = outboundBuffer.get(i);
                Entry entry = (Entry) entryObj;
                if (entry.promise.setUncancellable()) {
                    unflushedBytes -= entry.buffer.readableBytes();
                    parent.write(entry.buffer).cascadeTo(entry.promise);
                } else {
                    unflushedBytes -= entry.buffer.readableBytes();
                    entry.buffer.close();
                }
                entry.recycle();
            }
            outboundBuffer.clear();
            parent.flush();
        }

        @Override
        public ChannelOutboundBuffer outboundBuffer() {
            // We do not use ChannelOutboundBuffer
            return null;
        }

        boolean isWritable() {
            return !unwritable;
        }

        long bytesBeforeUnwritable() {
            long bytes = config().getWriteBufferHighWaterMark() - unflushedBytes;
            // If bytes is negative we know we are not writable, but if bytes is non-negative we have to check
            // writability.
            if (bytes > 0) {
                return isWritable() ? bytes : 0;
            }
            return 0;
        }

        long bytesBeforeWritable() {
            long bytes = unflushedBytes - config().getWriteBufferLowWaterMark();
            // If bytes is negative we know we are writable, but if bytes is non-negative we have to check writability.
            if (bytes > 0) {
                return isWritable() ? 0 : bytes;
            }
            return 0;
        }
    }

    static final class Entry {
        private static final ObjectPool<Entry> RECYCLER = ObjectPool.newPool(Entry::new);

        private final Handle<Entry> handle;
        Buffer buffer;
        Promise<Void> promise;

        private Entry(Handle<Entry> handle) {
            this.handle = handle;
        }

        static Entry newInstance(Buffer buffer, Promise<Void> promise) {
            Entry entry = RECYCLER.get();
            entry.buffer = buffer;
            entry.promise = promise;
            return entry;
        }

        void recycle() {
            buffer = null;
            promise = null;
            handle.recycle(this);
        }
    }

    private static final class UnsafeState {
        private static final byte REGISTER_STARTED = 1;
        private static final byte RESERVED = 1 << 1;
        private static final byte REGISTERED = 1 << 2;
        private static final byte READ_IDLE = 1 << 3;
        private static final byte READ_REQUESTED = 1 << 4;
        private static final byte READ_IN_PROGRESS = 1 << 5;
        private static final byte READ_IDLE_MASK = Byte.MAX_VALUE & ~READ_REQUESTED & ~READ_IN_PROGRESS;
        private static final byte READ_REQUESTED_MASK = Byte.MAX_VALUE & ~READ_IDLE & ~READ_IN_PROGRESS;
        private static final byte READ_IN_PROGRESS_MASK = Byte.MAX_VALUE & ~READ_IDLE & ~READ_REQUESTED;

        private UnsafeState() {
        }

        static byte setRegisterStarted(byte state) {
            return (byte) (state | REGISTER_STARTED);
        }

        static boolean isRegistered(byte state) {
            return (state & REGISTER_STARTED) == REGISTER_STARTED;
        }

        static byte setRegistered(byte state) {
            return (byte) (state | REGISTERED);
        }

        static boolean isReserved(byte state) {
            return (state & REGISTER_STARTED) == REGISTER_STARTED;
        }

        static byte setReserved(byte state) {
            return (byte) (state | RESERVED);
        }

        static boolean isReadIdle(byte state) {
            return (state & READ_IDLE) == READ_IDLE;
        }

        static byte setReadIdle(byte state) {
            return (byte) (state & READ_IDLE_MASK);
        }

        static byte setReadRequested(byte state) {
            return (byte) (state & READ_REQUESTED_MASK);
        }

        static boolean isReadRequested(byte state) {
            return (state & READ_REQUESTED) == READ_REQUESTED;
        }

        static byte setReadInProgress(byte state) {
            return (byte) (state & READ_IN_PROGRESS_MASK);
        }
    }
}

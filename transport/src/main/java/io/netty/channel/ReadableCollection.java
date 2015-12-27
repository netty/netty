/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A wrapper class which can combine several {@link ByteBuf}s and {@link FileRegion}s together.
 */
public final class ReadableCollection {

    public static final ReadableCollection EMPTY = new ReadableCollection(new ArrayDeque<Component>(), 0L);

    private static final class Component {
        ReferenceCounted obj;
        long length;
        final boolean isByteBuf;

        public Component(ReferenceCounted obj, long length, boolean isByteBuf) {
            this.obj = obj;
            this.length = length;
            this.isByteBuf = isByteBuf;
        }
    }

    private final Deque<Component> components;

    private long readableBytes;

    private ReadableCollection(Deque<Component> components, long readableBytes) {
        this.components = components;
        this.readableBytes = readableBytes;
    }

    /**
     * Return a {@link ByteBuf} if it is the only element in this collection.
     * <p>
     * Usually you should call this first because lots of netty's modules are optimized for {@link ByteBuf}.
     */
    public ByteBuf unbox() {
        if (components.size() == 1) {
            Component c = components.peekFirst();
            if (c.isByteBuf) {
                return (ByteBuf) c.obj;
            }
        }
        return null;
    }

    /**
     * Return true iff {@link #readableBytes()} {@code > 0}
     */
    public boolean isReadable() {
        return readableBytes > 0;
    }

    /**
     * The number of readable bytes.
     */
    public long readableBytes() {
        return readableBytes;
    }

    private Object slice(Component c, long length) {
        c.length -= length;
        if (c.isByteBuf) {
            ByteBuf buf = (ByteBuf) c.obj;
            return buf.readSlice((int) length).retain();
        } else {
            FileRegion region = (FileRegion) c.obj;
            return region.transferSlice(length).retain();
        }
    }

    private long checkLength(long length) {
        if (length < 0) {
            throw new IllegalArgumentException("length (expected >= 0): " + length);
        }
        return Math.min(length, readableBytes);
    }

    private interface WriteTarget {

        ChannelFuture newSucceededFuture();

        ChannelPromise newPromise();

        ChannelFuture write(Object msg);

        ChannelFuture write(Object msg, ChannelPromise promise);
    }

    private ChannelFuture writeMultiTo(WriteTarget target, ChannelPromise promise, long length) throws IOException {
        List<ChannelPromise> pendingPromiseList = new ArrayList<ChannelPromise>();
        for (long remaining = length;;) {
            Component c = components.peekFirst();
            if (c.length > remaining) {
                ChannelPromise p = target.newPromise();
                target.write(slice(c, remaining), p);
                pendingPromiseList.add(p);
                break;
            } else {
                ChannelPromise p = target.newPromise();
                target.write(c.obj, p);
                pendingPromiseList.add(p);
                components.pollFirst();
                remaining -= c.length;
                if (remaining == 0) {
                    break;
                }
            }
        }
        ChannelPromiseAggregator aggregator = new ChannelPromiseAggregator(promise);
        aggregator.add(pendingPromiseList.toArray(new ChannelPromise[0]));
        return promise;
    }

    private ChannelFuture writeTo(WriteTarget target, long length) throws IOException {
        length = checkLength(length);
        if (length == 0) {
            return target.newSucceededFuture();
        }
        readableBytes -= length;
        Component c = components.peekFirst();
        if (c.length == length) {
            components.pollFirst();
            return target.write(c.obj);
        }
        if (c.length > length) {
            return target.write(slice(c, length));
        }
        return writeMultiTo(target, target.newPromise(), length);
    }

    private ChannelFuture writeTo(WriteTarget target, ChannelPromise promise, long length) throws IOException {
        length = checkLength(length);
        if (length == 0) {
            return target.newSucceededFuture();
        }
        readableBytes -= length;
        Component c = components.peekFirst();
        if (c.length == length) {
            components.pollFirst();
            return target.write(c.obj, promise);
        }
        if (c.length > length) {
            return target.write(slice(c, length), promise);
        }
        return writeMultiTo(target, promise, length);
    }

    private static final class ChannelWriteTarget implements WriteTarget {

        private final Channel ch;

        public ChannelWriteTarget(Channel ch) {
            this.ch = ch;
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return ch.write(msg, promise);
        }

        @Override
        public ChannelFuture write(Object msg) {
            return ch.write(msg);
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return ch.newSucceededFuture();
        }

        @Override
        public ChannelPromise newPromise() {
            return ch.newPromise();
        }
    }

    /**
     * Call {@link Channel#write(Object)}.
     *
     * @param length
     *            the maximum number of bytes to transfer, if larger than {@link #readableBytes()}, then only
     *            {@link #readableBytes()} bytes will be write out.
     */
    public ChannelFuture writeTo(final Channel ch, long length) throws IOException {
        return writeTo(new ChannelWriteTarget(ch), length);
    }

    /**
     * Call {@link Channel#write(Object, ChannelPromise)}.
     *
     * @param length
     *            the maximum number of bytes to transfer, if larger than {@link #readableBytes()}, then only
     *            {@link #readableBytes()} bytes will be write out.
     */
    public ChannelFuture writeTo(Channel ch, ChannelPromise promise, long length) throws IOException {
        return writeTo(new ChannelWriteTarget(ch), promise, length);
    }

    private static final class ContextWriteTarget implements WriteTarget {

        private final ChannelHandlerContext ctx;

        public ContextWriteTarget(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return ctx.write(msg, promise);
        }

        @Override
        public ChannelFuture write(Object msg) {
            return ctx.write(msg);
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return ctx.newSucceededFuture();
        }

        @Override
        public ChannelPromise newPromise() {
            return ctx.newPromise();
        }
    }

    /**
     * Call {@link ChannelHandlerContext#write(Object)}.
     *
     * @param length
     *            the maximum number of bytes to transfer, if larger than {@link #readableBytes()}, then only
     *            {@link #readableBytes()} bytes will be write out.
     */
    public ChannelFuture writeTo(ChannelHandlerContext ctx, long length) throws IOException {
        return writeTo(new ContextWriteTarget(ctx), length);
    }

    /**
     * Call {@link ChannelHandlerContext#write(Object, ChannelPromise)}.
     *
     * @param length
     *            the maximum number of bytes to transfer, if larger than {@link #readableBytes()}, then only
     *            {@link #readableBytes()} bytes will be write out.
     */
    public ChannelFuture writeTo(ChannelHandlerContext ctx, ChannelPromise promise, long length) throws IOException {
        return writeTo(new ContextWriteTarget(ctx), promise, length);
    }

    private static final class PipelineWriteTarget implements WriteTarget {

        private final ChannelPipeline pipeline;

        public PipelineWriteTarget(ChannelPipeline pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return pipeline.write(msg, promise);
        }

        @Override
        public ChannelFuture write(Object msg) {
            return pipeline.write(msg);
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return pipeline.channel().newSucceededFuture();
        }

        @Override
        public ChannelPromise newPromise() {
            return pipeline.channel().newPromise();
        }
    }

    /**
     * Call {@link ChannelPipeline#write(Object)}.
     *
     * @param length
     *            the maximum number of bytes to transfer, if larger than {@link #readableBytes()}, then only
     *            {@link #readableBytes()} bytes will be write out.
     */
    public ChannelFuture writeTo(ChannelPipeline pipeline, long length) throws IOException {
        return writeTo(new PipelineWriteTarget(pipeline), length);
    }

    /**
     * Call {@link ChannelPipeline#write(Object, ChannelPromise)}.
     *
     * @param length
     *            the maximum number of bytes to transfer, if larger than {@link #readableBytes()}, then only
     *            {@link #readableBytes()} bytes will be write out.
     */
    public ChannelFuture writeTo(ChannelPipeline pipeline, ChannelPromise promise, long length) throws IOException {
        return writeTo(new PipelineWriteTarget(pipeline), promise, length);
    }

    public void clear() {
        for (Component c; (c = components.poll()) != null;) {
            c.obj.release();
        }
        this.readableBytes = 0;
    }

    /**
     * Fast path to create a {@link ReadableCollection} which only contains on FileRegion as its elements.
     */
    public static ReadableCollection of(FileRegion region) {
        Deque<Component> components = new ArrayDeque<Component>();
        components.addLast(new Component(region, region.transferableBytes(), false));
        return new ReadableCollection(components, region.transferableBytes());
    }

    /**
     * Get a Builder to build {@link ReadableCollection}.
     */
    public static Builder create(ByteBufAllocator alloc, int maxNumComponents) {
        return new Builder(alloc, maxNumComponents);
    }

    public static final class Builder {

        private final ByteBufAllocator alloc;

        private final int maxNumComponents;

        private final Deque<Component> components = new ArrayDeque<Component>();

        private long readableBytes;

        private Builder(ByteBufAllocator alloc, int maxNumComponents) {
            this.alloc = alloc;
            this.maxNumComponents = maxNumComponents;
        }

        private ByteBuf compose(ByteBuf current, ByteBuf next) {
            return CoalescingBufferQueue.compose(alloc, maxNumComponents, current, next);
        }

        private void addComponent(ByteBuf buf) {
            int readableBytes = buf.readableBytes();
            Component last = components.peekLast();
            if (last != null && last.isByteBuf) {
                last.length += readableBytes;
                last.obj = compose((ByteBuf) last.obj, buf);
            } else {
                components.addLast(new Component(buf, readableBytes, true));
            }
        }

        /**
         * Add a {@link ByteBuf}.
         */
        public Builder add(ByteBuf buf) {
            this.readableBytes += ObjectUtil.checkNotNull(buf, "buf").readableBytes();
            addComponent(buf);
            return this;
        }

        /**
         * Add a {@link FileRegion}
         */
        public Builder add(FileRegion region) {
            long transferableBytes = ObjectUtil.checkNotNull(region, "region").transferableBytes();
            this.readableBytes += transferableBytes;
            components.addLast(new Component(region, transferableBytes, false));
            return this;
        }

        /**
         * Add a {@link ReadableCollection} with maximum {@code length} data.
         */
        public Builder add(ReadableCollection rc, long length) {
            long readableBytes = ObjectUtil.checkNotNull(rc, "ReadableCollection").readableBytes();
            if (length > readableBytes) {
                throw new IndexOutOfBoundsException("max " + readableBytes + ", got " + length);
            }
            for (long remaining = length;;) {
                Component c = rc.components.peekFirst();
                if (c.isByteBuf) {
                    if (c.length > remaining) {
                        c.length -= remaining;
                        addComponent(((ByteBuf) c.obj).readSlice((int) remaining).retain());
                        break;
                    } else {
                        addComponent((ByteBuf) c.obj);
                        rc.components.pollFirst();
                        remaining -= c.length;
                        if (remaining == 0) {
                            break;
                        }
                    }
                } else { // FileRegion
                    if (c.length > remaining) {
                        c.length -= remaining;
                        components.addLast(new Component(((FileRegion) c.obj).transferSlice(remaining).retain(),
                                remaining, false));
                        break;
                    } else {
                        components.addLast(c);
                        rc.components.pollFirst();
                        remaining -= c.length;
                        if (remaining == 0) {
                            break;
                        }
                    }
                }
            }
            this.readableBytes += length;
            rc.readableBytes -= length;
            return this;
        }

        /**
         * Create the {@link ReadableCollection} instance.
         */
        public ReadableCollection build() {
            return new ReadableCollection(components, readableBytes);
        }
    }
}

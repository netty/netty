/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.embedder;

import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;

import java.lang.reflect.Array;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A skeletal {@link CodecEmbedder} implementation.
 */
abstract class AbstractCodecEmbedder<E> implements CodecEmbedder<E> {

    private static final EventLoop loop = new EmbeddedEventLoop();

    private final Queue<Object> productQueue = new LinkedList<Object>();
    private final Channel channel = new EmbeddedChannel(productQueue);

    /**
     * Creates a new embedder whose pipeline is composed of the specified
     * handlers.
     */
    protected AbstractCodecEmbedder(ChannelHandler... handlers) {
        channel.pipeline().addLast(handlers);
        channel.pipeline().addLast(new LastHandler());
        loop.register(channel);
    }

    @Override
    public boolean finish() {
        channel.pipeline().close().syncUninterruptibly();
        return !productQueue.isEmpty();
    }

    /**
     * Returns the virtual {@link Channel} which will be used as a mock
     * during encoding and decoding.
     */
    protected final Channel channel() {
        return channel;
    }

    /**
     * Returns {@code true} if and only if the produce queue is empty and
     * therefore {@link #poll()} will return {@code null}.
     */
    protected final boolean isEmpty() {
        return productQueue.isEmpty();
    }

    @Override
    public final E poll() {
        return product(productQueue.poll());
    }

    @Override
    public final E peek() {
        return product(productQueue.peek());
    }

    @SuppressWarnings("unchecked")
    private E product(Object p) {
        if (p instanceof Throwable) {
            if (p instanceof RuntimeException) {
                throw (RuntimeException) p;
            }
            if (p instanceof Error) {
                throw (Error) p;
            }
            throw new ChannelException((Throwable) p);
        }
        return (E) p;
    }

    @Override
    public final Object[] pollAll() {
        final int size = size();
        Object[] a = new Object[size];
        for (int i = 0; i < size; i ++) {
            E product = poll();
            if (product == null) {
                throw new ConcurrentModificationException();
            }
            a[i] = product;
        }
        return a;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T[] pollAll(T[] a) {
        if (a == null) {
            throw new NullPointerException("a");
        }

        final int size = size();

        // Create a new array if the specified one is too small.
        if (a.length < size) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        }

        for (int i = 0;; i ++) {
            T product = (T) poll();
            if (product == null) {
                break;
            }
            a[i] = product;
        }

        // Put the terminator if necessary.
        if (a.length > size) {
            a[size] = null;
        }

        return a;
    }

    @Override
    public final int size() {
        return productQueue.size();
    }

    @Override
    public ChannelPipeline pipeline() {
        return channel.pipeline();
    }

    private final class LastHandler extends ChannelInboundHandlerAdapter<Object> {
        @Override
        public ChannelBufferHolder<Object> newInboundBuffer(
                ChannelInboundHandlerContext<Object> ctx) throws Exception {
            return ChannelBufferHolders.messageBuffer(productQueue);
        }

        @Override
        public void exceptionCaught(ChannelInboundHandlerContext<Object> ctx, Throwable cause) throws Exception {
            productQueue.add(cause);
        }
    }
}

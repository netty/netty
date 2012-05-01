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

import static io.netty.channel.Channels.*;

import java.lang.reflect.Array;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.Queue;

import io.netty.channel.Channels;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineException;
import io.netty.channel.ChannelSink;
import io.netty.channel.ChannelUpstreamHandler;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.MessageEvent;

/**
 * A skeletal {@link CodecEmbedder} implementation.
 */
abstract class AbstractCodecEmbedder<E> implements CodecEmbedder<E> {

    private final Channel channel;
    private final ChannelPipeline pipeline;
    private final EmbeddedChannelSink sink = new EmbeddedChannelSink();

    final Queue<Object> productQueue = new LinkedList<Object>();

    /**
     * Creates a new embedder whose pipeline is composed of the specified
     * handlers.
     */
    protected AbstractCodecEmbedder(ChannelHandler... handlers) {
        pipeline = new EmbeddedChannelPipeline();
        configurePipeline(handlers);
        channel = new EmbeddedChannel(pipeline, sink);
        fireInitialEvents();
    }

    /**
     * Creates a new embedder whose pipeline is composed of the specified
     * handlers.
     *
     * @param bufferFactory the {@link ChannelBufferFactory} to be used when
     *                      creating a new buffer.
     */
    protected AbstractCodecEmbedder(ChannelBufferFactory bufferFactory, ChannelHandler... handlers) {
        this(handlers);
        getChannel().getConfig().setBufferFactory(bufferFactory);
    }

    private void fireInitialEvents() {
        // Fire the typical initial events.
        fireChannelOpen(channel);
        fireChannelBound(channel, channel.getLocalAddress());
        fireChannelConnected(channel, channel.getRemoteAddress());
    }

    private void configurePipeline(ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        if (handlers.length == 0) {
            throw new IllegalArgumentException(
                    "handlers should contain at least one " +
                    ChannelHandler.class.getSimpleName() + '.');
        }

        for (int i = 0; i < handlers.length; i ++) {
            ChannelHandler h = handlers[i];
            if (h == null) {
                throw new NullPointerException("handlers[" + i + "]");
            }
            pipeline.addLast(String.valueOf(i), handlers[i]);
        }
        pipeline.addLast("SINK", sink);
    }

    @Override
    public boolean finish() {
        close(channel);
        fireChannelDisconnected(channel);
        fireChannelUnbound(channel);
        fireChannelClosed(channel);
        return !productQueue.isEmpty();
    }

    /**
     * Returns the virtual {@link Channel} which will be used as a mock
     * during encoding and decoding.
     */
    protected final Channel getChannel() {
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
    @SuppressWarnings("unchecked")
    public final E poll() {
        return (E) productQueue.poll();
    }

    @Override
    @SuppressWarnings("unchecked")
    public final E peek() {
        return (E) productQueue.peek();
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
    public ChannelPipeline getPipeline() {
        return pipeline;
    }

    private final class EmbeddedChannelSink implements ChannelSink, ChannelUpstreamHandler {
        EmbeddedChannelSink() {
        }

        @Override
        public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) {
            handleEvent(e);
        }

        @Override
        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) {
            handleEvent(e);
        }

        private void handleEvent(ChannelEvent e) {
            if (e instanceof MessageEvent) {
                boolean offered = productQueue.offer(((MessageEvent) e).getMessage());
                assert offered;
            } else if (e instanceof ExceptionEvent) {
                throw new CodecEmbedderException(((ExceptionEvent) e).cause());
            }

            // Swallow otherwise.
        }

        @Override
        public void exceptionCaught(
                ChannelPipeline pipeline, ChannelEvent e,
                ChannelPipelineException cause) throws Exception {
            Throwable actualCause = cause.getCause();
            if (actualCause == null) {
                actualCause = cause;
            }

            throw new CodecEmbedderException(actualCause);
        }

        @Override
        public ChannelFuture execute(ChannelPipeline pipeline, Runnable task) {
            try {
                task.run();
                return Channels.succeededFuture(pipeline.channel());
            } catch (Throwable t) {
                return Channels.failedFuture(pipeline.channel(), t);
            }
        }
    }

    private static final class EmbeddedChannelPipeline extends DefaultChannelPipeline {

        EmbeddedChannelPipeline() {
        }

        @Override
        protected void notifyHandlerException(ChannelEvent e, Throwable t) {
            while (t instanceof ChannelPipelineException && t.getCause() != null) {
                t = t.getCause();
            }
            if (t instanceof CodecEmbedderException) {
                throw (CodecEmbedderException) t;
            } else {
                throw new CodecEmbedderException(t);
            }
        }
    }
}

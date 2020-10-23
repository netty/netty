/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.group;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.BlockingOperationException;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.internal.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * The default {@link ChannelGroupFuture} implementation.
 */
final class DefaultChannelGroupFuture extends DefaultPromise<Void> implements ChannelGroupFuture {

    private final ChannelGroup group;
    private final Map<Channel, ChannelFuture> futures;
    private int successCount;
    private int failureCount;

    private final ChannelFutureListener childListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            boolean success = future.isSuccess();
            boolean callSetDone;
            synchronized (DefaultChannelGroupFuture.this) {
                if (success) {
                    successCount ++;
                } else {
                    failureCount ++;
                }

                callSetDone = successCount + failureCount == futures.size();
                assert successCount + failureCount <= futures.size();
            }

            if (callSetDone) {
                if (failureCount > 0) {
                    List<Map.Entry<Channel, Throwable>> failed =
                            new ArrayList<Map.Entry<Channel, Throwable>>(failureCount);
                    for (ChannelFuture f: futures.values()) {
                        if (!f.isSuccess()) {
                            failed.add(new DefaultEntry<Channel, Throwable>(f.channel(), f.cause()));
                        }
                    }
                    setFailure0(new ChannelGroupException(failed));
                } else {
                    setSuccess0();
                }
            }
        }
    };

    /**
     * Creates a new instance.
     */
    DefaultChannelGroupFuture(ChannelGroup group, Collection<ChannelFuture> futures,  EventExecutor executor) {
        super(executor);
        this.group = ObjectUtil.checkNotNull(group, "group");
        ObjectUtil.checkNotNull(futures, "futures");

        Map<Channel, ChannelFuture> futureMap = new LinkedHashMap<Channel, ChannelFuture>();
        for (ChannelFuture f: futures) {
            futureMap.put(f.channel(), f);
        }

        this.futures = Collections.unmodifiableMap(futureMap);

        for (ChannelFuture f: this.futures.values()) {
            f.addListener(childListener);
        }

        // Done on arrival?
        if (this.futures.isEmpty()) {
            setSuccess0();
        }
    }

    DefaultChannelGroupFuture(ChannelGroup group, Map<Channel, ChannelFuture> futures, EventExecutor executor) {
        super(executor);
        this.group = group;
        this.futures = Collections.unmodifiableMap(futures);
        for (ChannelFuture f: this.futures.values()) {
            f.addListener(childListener);
        }

        // Done on arrival?
        if (this.futures.isEmpty()) {
            setSuccess0();
        }
    }

    @Override
    public ChannelGroup group() {
        return group;
    }

    @Override
    public ChannelFuture find(Channel channel) {
        return futures.get(channel);
    }

    @Override
    public Iterator<ChannelFuture> iterator() {
        return futures.values().iterator();
    }

    @Override
    public synchronized boolean isPartialSuccess() {
        return successCount != 0 && successCount != futures.size();
    }

    @Override
    public synchronized boolean isPartialFailure() {
        return failureCount != 0 && failureCount != futures.size();
    }

    @Override
    public DefaultChannelGroupFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public DefaultChannelGroupFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public DefaultChannelGroupFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public DefaultChannelGroupFuture removeListeners(
            GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public DefaultChannelGroupFuture await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public DefaultChannelGroupFuture awaitUninterruptibly() {
        super.awaitUninterruptibly();
        return this;
    }

    @Override
    public DefaultChannelGroupFuture syncUninterruptibly() {
        super.syncUninterruptibly();
        return this;
    }

    @Override
    public DefaultChannelGroupFuture sync() throws InterruptedException {
        super.sync();
        return this;
    }

    @Override
    public ChannelGroupException cause() {
        return (ChannelGroupException) super.cause();
    }

    private void setSuccess0() {
        super.setSuccess(null);
    }

    private void setFailure0(ChannelGroupException cause) {
        super.setFailure(cause);
    }

    @Override
    public DefaultChannelGroupFuture setSuccess(Void result) {
        throw new IllegalStateException();
    }

    @Override
    public boolean trySuccess(Void result) {
        throw new IllegalStateException();
    }

    @Override
    public DefaultChannelGroupFuture setFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    @Override
    protected void checkDeadLock() {
        EventExecutor e = executor();
        if (e != null && e != ImmediateEventExecutor.INSTANCE && e.inEventLoop()) {
            throw new BlockingOperationException();
        }
    }

    private static final class DefaultEntry<K, V> implements Map.Entry<K, V> {
        private final K key;
        private final V value;

        DefaultEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException("read-only");
        }
    }
}

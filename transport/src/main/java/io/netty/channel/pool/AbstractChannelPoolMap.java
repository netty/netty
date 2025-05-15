/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.pool;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ReadOnlyIterator;

import java.io.Closeable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A skeletal {@link ChannelPoolMap} implementation. To find the right {@link ChannelPool}
 * the {@link Object#hashCode()} and {@link Object#equals(Object)} is used.
 */
public abstract class AbstractChannelPoolMap<K, P extends ChannelPool>
        implements ChannelPoolMap<K, P>, Iterable<Entry<K, P>>, Closeable {
    private final ConcurrentMap<K, P> map = new ConcurrentHashMap<>();

    @Override
    public final P get(K key) {
        P pool = map.get(checkNotNull(key, "key"));
        if (pool == null) {
            pool = newPool(key);
            P old = map.putIfAbsent(key, pool);
            if (old != null) {
                // We need to destroy the newly created pool as we not use it.
                poolCloseAsyncIfSupported(pool);
                pool = old;
            }
        }
        return pool;
    }
    /**
     * Remove the {@link ChannelPool} from this {@link AbstractChannelPoolMap}. Returns {@code true} if removed,
     * {@code false} otherwise.
     *
     * If the removed pool extends {@link SimpleChannelPool} it will be closed asynchronously to avoid blocking in
     * this method.
     *
     * Please note that {@code null} keys are not allowed.
     */
    public final boolean remove(K key) {
        P pool =  map.remove(checkNotNull(key, "key"));
        if (pool != null) {
            poolCloseAsyncIfSupported(pool);
            return true;
        }
        return false;
    }

    /**
     * Remove the {@link ChannelPool} from this {@link AbstractChannelPoolMap}. Returns a future that comletes with a
     * {@code true} result if the pool has been removed by this call, otherwise the result is {@code false}.
     *
     * If the removed pool extends {@link SimpleChannelPool} it will be closed asynchronously to avoid blocking in
     * this method. The returned future will be completed once this asynchronous pool close operation completes.
     */
    private Future<Boolean> removeAsyncIfSupported(K key) {
        P pool =  map.remove(checkNotNull(key, "key"));
        if (pool != null) {
            final Promise<Boolean> removePromise = GlobalEventExecutor.INSTANCE.newPromise();
            poolCloseAsyncIfSupported(pool).addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) throws Exception {
                    if (future.isSuccess()) {
                        removePromise.setSuccess(Boolean.TRUE);
                    } else {
                        removePromise.setFailure(future.cause());
                    }
                }
            });
            return removePromise;
        }
        return GlobalEventExecutor.INSTANCE.newSucceededFuture(Boolean.FALSE);
    }

    /**
     * If the pool implementation supports asynchronous close, then use it to avoid a blocking close call in case
     * the ChannelPoolMap operations are called from an EventLoop.
     *
     * @param pool the ChannelPool to be closed
     */
    private static Future<Void> poolCloseAsyncIfSupported(ChannelPool pool) {
        if (pool instanceof SimpleChannelPool) {
            return ((SimpleChannelPool) pool).closeAsync();
        } else {
            try {
                pool.close();
                return GlobalEventExecutor.INSTANCE.newSucceededFuture(null);
            } catch (Exception e) {
                return GlobalEventExecutor.INSTANCE.newFailedFuture(e);
            }
        }
    }

    @Override
    public final Iterator<Entry<K, P>> iterator() {
        return new ReadOnlyIterator<Entry<K, P>>(map.entrySet().iterator());
    }

    /**
     * Returns the number of {@link ChannelPool}s currently in this {@link AbstractChannelPoolMap}.
     */
    public final int size() {
        return map.size();
    }

    /**
     * Returns {@code true} if the {@link AbstractChannelPoolMap} is empty, otherwise {@code false}.
     */
    public final boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public final boolean contains(K key) {
        return map.containsKey(checkNotNull(key, "key"));
    }

    /**
     * Called once a new {@link ChannelPool} needs to be created as non exists yet for the {@code key}.
     */
    protected abstract P newPool(K key);

    @Override
    public final void close() {
        for (K key: map.keySet()) {
            // Wait for remove to finish to ensure that resources are released before returning from close
            removeAsyncIfSupported(key).syncUninterruptibly();
        }
    }
}

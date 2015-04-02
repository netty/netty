/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.pool.ChannelPoolSegmentFactory.ChannelPoolSegment;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Simple {@link ChannelPool} implementation which will create new {@link Channel}s if someone tries to acquire
 * a {@link Channel} but none is in the pool atm. No limit on the maximal concurrent {@link Channel}s is enforced.
 *
 * @param <C>   the {@link Channel} type to pool.
 * @param <K>   the {@link ChannelPoolKey} that is used to store and lookup the {@link Channel}s.
 */
public class SimpleChannelPool<C extends Channel, K extends ChannelPoolKey> implements ChannelPool<C, K> {
    static final IllegalStateException FULL_EXCEPTION =
            new IllegalStateException("Too many outstanding acquire operations");
    static {
        FULL_EXCEPTION.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    private final ConcurrentMap<ChannelPoolKey, ChannelPoolSegment<C, K>> pool =
            PlatformDependent.newConcurrentHashMap();
    private final ChannelPoolHandler<C, K> handler;
    private final ChannelHealthChecker<C, K> healthCheck;
    private final ChannelPoolSegmentFactory<C, K> segmentFactory;
    private final ChannelFactory<?> channelFactory;
    private final Bootstrap bootstrap;

    /**
     * Creates a new instance using the {@link ActiveChannelHealthChecker} and a {@link ChannelPoolSegmentFactory} that
     * process things in LIFO order.
     *
     * @param bootstrap         the {@link Bootstrap} that is used for connections
     * @param handler           the {@link ChannelPoolHandler} that will be notified for the different pool actions
     */
    public SimpleChannelPool(Bootstrap bootstrap, final ChannelPoolHandler<C, K> handler) {
        this(bootstrap, handler,
             ActiveChannelHealthChecker.<C, K>instance(), ChannelPoolSegmentFactories.<C, K>newLifoFactory());
    }

    /**
     * Creates a new instance.
     *
     * @param bootstrap         the {@link Bootstrap} that is used for connections
     * @param handler           the {@link ChannelPoolHandler} that will be notified for the different pool actions
     * @param healthCheck       the {@link ChannelHealthChecker} that will be used to check if a {@link Channel} is
     *                          still healty when obtain from the {@link ChannelPool}
     * @param segmentFactory    the {@link ChannelPoolSegmentFactory} that will be used to create new
     *                          {@link ChannelPoolSegment}s when needed
     */
    public SimpleChannelPool(Bootstrap bootstrap, final ChannelPoolHandler<C, K> handler,
                             final ChannelHealthChecker<C, K> healthCheck,
                             final ChannelPoolSegmentFactory<C, K> segmentFactory) {
        this.handler = checkNotNull(handler, "handler");
        this.healthCheck = checkNotNull(healthCheck, "healthCheck");
        this.segmentFactory = checkNotNull(segmentFactory, "segmentFactory");
        channelFactory = bootstrap.channelFactory();
        this.bootstrap = checkNotNull(bootstrap, "bootstrap").clone();
        this.bootstrap.handler(new ChannelInitializer<PooledChannel<C, K>>() {
            @SuppressWarnings("unchecked")
            @Override
            protected void initChannel(PooledChannel<C, K> ch) throws Exception {
                assert ch.eventLoop().inEventLoop();
                handler.channelCreated(ch);
            }
        });
    }

    private EventLoop loop(K key) {
        EventLoop loop = key.eventLoop();
        if (loop == null) {
            loop = bootstrap.group().next();
        }
        return loop;
    }

    @Override
    public final Future<PooledChannel<C, K>> acquire(K key) {
        return acquire(key, loop(key).<PooledChannel<C, K>>newPromise());
    }

    @Override
    public Future<PooledChannel<C, K>> acquire(final K key, final Promise<PooledChannel<C, K>> promise) {
        checkNotNull(key, "key");
        checkNotNull(promise, "promise");
        try {
            ChannelPoolSegment<C, K> channels = pool.get(key);
            if (channels == null) {
                newChannel(key, promise);
                return promise;
            }

            final SimplePooledChannel ch = (SimplePooledChannel) channels.poll();
            if (ch == null) {
                newChannel(key, promise);
                return promise;
            }
            EventLoop loop = ch.eventLoop();
            if (loop.inEventLoop()) {
                doHealthCheck(ch, promise);
            } else {
                loop.execute(new OneTimeTask() {
                    @Override
                    public void run() {
                        doHealthCheck(ch, promise);
                    }
                });
            }
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
        return promise;
    }

    private void doHealthCheck(final SimplePooledChannel ch, final Promise<PooledChannel<C, K>> promise) {
        assert ch.eventLoop().inEventLoop();

        Future<Boolean> f = healthCheck.isHealthy(ch);
        if (f.isDone()) {
            notifyHealthCheck(f, ch, promise);
        } else {
            f.addListener(new FutureListener<Boolean>() {
                @Override
                public void operationComplete(Future<Boolean> future) throws Exception {
                    notifyHealthCheck(future, ch, promise);
                }
            });
        }
    }

    private void notifyHealthCheck(Future<? super Boolean> future,
                                   SimplePooledChannel ch, Promise<PooledChannel<C, K>> promise) {
        assert ch.eventLoop().inEventLoop();

        if (future.isSuccess()) {
            try {
                ch.acquired();
                handler.channelAcquired(ch);
                promise.setSuccess(ch);
            } catch (Throwable cause) {
                closeAndFail(ch, promise, cause);
            }
        } else {
            ch.close();
            acquire(ch.key(), promise);
        }
    }

    private void newChannel(
            final K key, final Promise<PooledChannel<C, K>> promise) {
        Bootstrap bs = bootstrap.clone(loop(key), new ChannelFactory<Channel>() {
            @SuppressWarnings("unchecked")
            @Override
            public Channel newChannel() {
                SimplePooledChannel ch = newPooledChannel((C) channelFactory.newChannel(), key);
                ch.acquired();
                return ch;
            }
        });

        ChannelFuture f = bs.connect(key.remoteAddress());
        if (f.isDone()) {
            notifyConnect(f, promise);
        } else {
            f.addListener(new ChannelFutureListener() {
                @SuppressWarnings("unchecked")
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    notifyConnect(future, promise);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void notifyConnect(ChannelFuture future, Promise<PooledChannel<C, K>> promise) {
        if (future.isSuccess()) {
            PooledChannel<C, K> ch = (PooledChannel<C, K>) future.channel();
            promise.setSuccess(ch);
        } else {
            promise.setFailure(future.cause());
        }
    }

    SimplePooledChannel newPooledChannel(C channel, K key) {
        return new SimplePooledChannel(channel, key, this);
    }

    class SimplePooledChannel extends AbstractPooledChannel<C, K> {
        public SimplePooledChannel(C channel, K key, SimpleChannelPool<C, K> pool) {
            super(channel, key, pool);
        }

        @Override
        public Future<Void> releaseToPool(final Promise<Void> promise) {
            checkNotNull(promise, "promise");
            try {
                final K key = key();
                ChannelPoolSegment<C, K> channels = pool.get(key);
                if (channels == null) {
                    channels = segmentFactory.newSegment();
                    ChannelPoolSegment<C, K> old = pool.putIfAbsent(key, channels);
                    if (old != null) {
                        channels = old;
                    }
                }
                final ChannelPoolSegment<C, K> channelQueue = channels;

                EventLoop loop = eventLoop();
                if (loop.inEventLoop()) {
                    doReleaseChannel(channelQueue, promise);
                } else {
                    loop.execute(new OneTimeTask() {
                        @Override
                        public void run() {
                            doReleaseChannel(channelQueue, promise);
                        }
                    });
                }
            } catch (Throwable cause) {
                closeAndFail(this, promise, cause);
            }
            return promise;
        }

        private void doReleaseChannel(ChannelPoolSegment<C, K> channels, Promise<Void> promise) {
            assert eventLoop().inEventLoop();

            try {
                if (channels.offer(this)) {
                    handler.channelReleased(this);
                    promise.setSuccess(null);
                } else {
                    closeAndFail(this, promise, FULL_EXCEPTION);
                }
            } catch (Throwable cause) {
                closeAndFail(this, promise, cause);
            }
        }
    }

    private static void closeAndFail(Channel ch, Promise<?> promise, Throwable cause) {
        ch.close();
        promise.setFailure(cause);
    }
}

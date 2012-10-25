/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket.aio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelTaskScheduler;
import io.netty.channel.EventExecutor;
import io.netty.channel.EventLoopException;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.DetectionUtil;

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AioEventLoopGroup extends MultithreadEventLoopGroup {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioEventLoopGroup.class);
    private static final AioChannelFinder CHANNEL_FINDER;

    static {
        AioChannelFinder finder;
        try {
            if (DetectionUtil.hasUnsafe()) {
                finder = new UnsafeAioChannelFinder();
            } else {
                finder = new ReflectiveAioChannelFinder();
            }
        } catch (Throwable t) {
            LOGGER.debug(String.format(
                    "Failed to instantiate the optimal %s implementation - falling back to %s.",
                    AioChannelFinder.class.getSimpleName(), ReflectiveAioChannelFinder.class.getSimpleName()), t);
            finder = new ReflectiveAioChannelFinder();
        }
        CHANNEL_FINDER = finder;
    }

    // Channel counter is initialized to one and shutdown will decrement it by one
    // therefore we can also use it for shutdown signaling.
    private final AtomicInteger channelCounter = new AtomicInteger(1);

    // To prevent childCounter from being decremented to often by calls to shutdown
    // we have to protect it with an AtomicBoolean (see shutdown below).
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final AioExecutorService groupExecutor = new AioExecutorService();
    final AsynchronousChannelGroup group;

    public AioEventLoopGroup() {
        this(0);
    }

    public AioEventLoopGroup(int nThreads) {
        this(nThreads, null);
    }

    public AioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
        try {
            group = AsynchronousChannelGroup.withThreadPool(groupExecutor);
        } catch (IOException e) {
            throw new EventLoopException("Failed to create an AsynchronousChannelGroup", e);
        }
    }

    private final ChannelFutureListener channelCloseListener = new ChannelFutureListener() {
      @Override
      public void operationComplete(final ChannelFuture future) throws Exception {
         removeChannel();
      }
    };

    private void removeChannel() {
       if (channelCounter.decrementAndGet() == 0) {
          // Close all connections and shut down event loop threads.
          super.shutdown();
       }
    }

    private boolean addChannel() {
       int c;
       do {
          c = channelCounter.get();
       } while (c != 0 && !channelCounter.compareAndSet(c, c + 1));
       return c > 0;
    }

    final void attach(final Channel channel) {
       if (! addChannel()) {
          channel.close();
          throw new ChannelException("AioEventLoopGroup already shutdown");
       }
       // Ensure that we are notified once the channel is closed.
       channel.closeFuture().addListener(channelCloseListener);
    }

    @Override
    public void shutdown() {
       // First of all ensure that we call removeChannel only once from within the shutdown
       // method. Just in case shutdown is called more then once by the client.
       if (shutdown.compareAndSet(false, true)) {
          // Tell JDK not to accept any more registration request.  Note that the threads
          // are not really shut down yet. The group will terminate once all its channels
          // are closed.
          group.shutdown();
          // Make sure the shutdown signal is set.
          removeChannel();
       }
    }

    @Override
    protected EventExecutor newChild(
            ThreadFactory threadFactory, ChannelTaskScheduler scheduler, Object... args) throws Exception {
          return new AioEventLoop(this, threadFactory, scheduler);
    }

    private void executeAioTask(Runnable command) {
        AbstractAioChannel ch = null;
        try {
            ch = CHANNEL_FINDER.findChannel(command);
        } catch (Throwable t) {
            // Ignore
        }

        EventExecutor l;
        if (ch != null) {
            l = ch.eventLoop();
        } else {
            l = next();
        }

        if (l.isShutdown()) {
            command.run();
        } else {
            l.execute(command);
        }
    }

    private final class AioExecutorService extends AbstractExecutorService {

        // It does not shut down the underlying EventExecutor - it merely pretends to be shut down.
        // The actual shut down is done by EventLoopGroup and EventLoop implementation.
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void shutdown() {
            latch.countDown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return latch.getCount() == 0;
        }

        @Override
        public boolean isTerminated() {
            return isShutdown();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            Class<? extends Runnable> commandType = command.getClass();
            if (commandType.getName().startsWith("sun.nio.ch.")) {
                executeAioTask(command);
            } else {
                next().execute(command);
            }
        }
    }
}

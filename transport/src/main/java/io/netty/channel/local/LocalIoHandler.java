/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.local;

import io.netty.channel.IoEventLoop;
import io.netty.channel.IoExecutionContext;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

public final class LocalIoHandler implements IoHandler {
    private final Set<LocalIoHandle> registeredChannels = new HashSet<LocalIoHandle>(64);
    private IoEventLoop eventLoop;
    private volatile Thread executionThread;

    private LocalIoHandler() { }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link LocalIoHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return new IoHandlerFactory() {
            @Override
            public IoHandler newHandler() {
                return new LocalIoHandler();
            }
        };
    }

    private static LocalIoHandle cast(IoHandle handle) {
        if (handle instanceof LocalIoHandle) {
            return (LocalIoHandle) handle;
        }
        throw new IllegalArgumentException("IoHandle of type " + StringUtil.simpleClassName(handle) + " not supported");
    }

    @Override
    public int run(IoExecutionContext runner) {
        if (executionThread == null) {
            executionThread = Thread.currentThread();
        }
        if (runner.canBlock()) {
            // Just block until there is a task ready to process or wakeup(...) is called.
            LockSupport.parkNanos(this, runner.delayNanos(System.nanoTime()));
        }
        return 0;
    }

    @Override
    public void initalize(IoEventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public void wakeup() {
        if (eventLoop == null || !eventLoop.inEventLoop()) {
            Thread thread = executionThread;
            if (thread != null) {
                // Wakeup if we block at the moment.
                LockSupport.unpark(thread);
            }
        }
    }

    @Override
    public void prepareToDestroy() {
        for (LocalIoHandle handle : registeredChannels) {
            handle.closeNow();
        }
        registeredChannels.clear();
    }

    @Override
    public void destroy() {
    }

    @Override
    public IoRegistration register(IoHandle handle) {
        LocalIoHandle localHandle = cast(handle);
        if (registeredChannels.add(localHandle)) {
            LocalIoRegistration registration = new LocalIoRegistration(eventLoop, localHandle);
            localHandle.registerNow();
            return registration;
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return LocalIoHandle.class.isAssignableFrom(handleType);
    }

    private final class LocalIoRegistration implements IoRegistration {
        private final Promise<?> cancellationPromise;
        private final IoEventLoop eventLoop;
        private final LocalIoHandle handle;

        LocalIoRegistration(IoEventLoop eventLoop, LocalIoHandle handle) {
            this.eventLoop = eventLoop;
            this.handle = handle;
            this.cancellationPromise = eventLoop.newPromise();
        }

        @Override
        public long submit(IoOps ops) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel() {
            if (!cancellationPromise.trySuccess(null)) {
                return;
            }
            if (eventLoop.inEventLoop()) {
                cancel0();
            } else {
                eventLoop.execute(this::cancel0);
            }
        }

        @Override
        public Future<?> cancelFuture() {
            return cancellationPromise;
        }

        private void cancel0() {
            if (registeredChannels.remove(handle)) {
                handle.deregisterNow();
            }
        }

        @Override
        public IoHandler ioHandler() {
            return LocalIoHandler.this;
        }
    }
}

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

import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.util.concurrent.ThreadAwareExecutor;
import io.netty.util.internal.StringUtil;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public final class LocalIoHandler implements IoHandler {
    private final Set<LocalIoHandle> registeredChannels = new HashSet<LocalIoHandle>(64);
    private final ThreadAwareExecutor executor;
    private volatile Thread executionThread;

    private LocalIoHandler(ThreadAwareExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link LocalIoHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return LocalIoHandler::new;
    }

    private static LocalIoHandle cast(IoHandle handle) {
        if (handle instanceof LocalIoHandle) {
            return (LocalIoHandle) handle;
        }
        throw new IllegalArgumentException("IoHandle of type " + StringUtil.simpleClassName(handle) + " not supported");
    }

    @Override
    public int run(IoHandlerContext context) {
        if (executionThread == null) {
            executionThread = Thread.currentThread();
        }
        if (context.canBlock()) {
            // Just block until there is a task ready to process or wakeup(...) is called.
            LockSupport.parkNanos(this, context.delayNanos(System.nanoTime()));
        }
        return 0;
    }

    @Override
    public void wakeup() {
        if (!executor.isExecutorThread(Thread.currentThread())) {
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
            LocalIoRegistration registration = new LocalIoRegistration(executor, localHandle);
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
        private final AtomicBoolean canceled = new AtomicBoolean();
        private final ThreadAwareExecutor executor;
        private final LocalIoHandle handle;

        LocalIoRegistration(ThreadAwareExecutor executor, LocalIoHandle handle) {
            this.executor = executor;
            this.handle = handle;
        }

        @Override
        public <T> T attachment() {
            return null;
        }

        @Override
        public long submit(IoOps ops) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValid() {
            return !canceled.get();
        }

        @Override
        public boolean cancel() {
            if (!canceled.compareAndSet(false, true)) {
                return false;
            }
            if (executor.isExecutorThread(Thread.currentThread())) {
                cancel0();
            } else {
                executor.execute(this::cancel0);
            }
            return true;
        }

        private void cancel0() {
            if (registeredChannels.remove(handle)) {
                handle.deregisterNow();
            }
        }
    }
}

/*
 * Copyright 2018 The Netty Project
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
package io.netty5.channel.local;

import io.netty5.channel.EventLoop;
import io.netty5.channel.IoExecutionContext;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoHandler;
import io.netty5.channel.IoHandlerFactory;
import io.netty5.channel.IoOps;
import io.netty5.channel.IoRegistration;
import io.netty5.util.internal.StringUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * {@link IoHandler} implementation for {@link LocalChannel} and {@link LocalServerChannel}.
 */
public final class LocalIoHandler implements IoHandler {
    private final Set<LocalIoHandle> registeredChannels = new HashSet<>(64);
    private volatile Thread executionThread;

    private LocalIoHandler() { }

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
    public void wakeup(EventLoop eventLoop) {
        if (!eventLoop.inEventLoop()) {
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
            handle.closeTransportNow();
        }
        registeredChannels.clear();
    }

    @Override
    public void destroy() {
    }

    @Override
    public IoRegistration register(EventLoop eventLoop, IoHandle handle) {
        LocalIoHandle localHandle = cast(handle);
        if (registeredChannels.add(localHandle)) {
            LocalIoRegistration registration = new LocalIoRegistration(eventLoop, localHandle);
            localHandle.registerTransportNow();
            return registration;
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return LocalIoHandle.class.isAssignableFrom(handleType);
    }

    private final class LocalIoRegistration extends AtomicBoolean implements IoRegistration {
        private final EventLoop eventLoop;
        private final LocalIoHandle handle;

        LocalIoRegistration(EventLoop eventLoop, LocalIoHandle handle) {
            this.eventLoop = eventLoop;
            this.handle = handle;
        }

        @Override
        public long submit(IoOps ops) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValid() {
            return !get();
        }

        @Override
        public void cancel() {
            if (getAndSet(true)) {
                return;
            }
            if (eventLoop.inEventLoop()) {
                cancel0();
            } else {
                eventLoop.execute(this::cancel0);
            }
        }

        private void cancel0() {
            if (registeredChannels.remove(handle)) {
                handle.deregisterTransportNow();
            }
        }

        @Override
        public IoHandler ioHandler() {
            return LocalIoHandler.this;
        }
    }
}

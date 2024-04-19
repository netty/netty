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

import io.netty.channel.IoExecutionContext;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.util.internal.StringUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

final class LocalIoHandler implements IoHandler {
    private final Set<LocalChannelIoHandle> registeredChannels = new HashSet<LocalChannelIoHandle>(64);
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

    private static LocalChannelIoHandle cast(IoHandle handle) {
        if (handle instanceof LocalChannelIoHandle) {
            return (LocalChannelIoHandle) handle;
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
    public void wakeup(boolean inEventLoop) {
        if (!inEventLoop) {
            Thread thread = executionThread;
            if (thread != null) {
                // Wakeup if we block at the moment.
                LockSupport.unpark(thread);
            }
        }
    }

    @Override
    public void prepareToDestroy() {
        for (LocalChannelIoHandle handle : registeredChannels) {
            handle.closeNow();
        }
        registeredChannels.clear();
    }

    @Override
    public void destroy() {
    }

    @Override
    public void register(IoHandle handle) {
        LocalChannelIoHandle localHandle = cast(handle);
        if (registeredChannels.add(localHandle)) {
            localHandle.registerNow();
        }
    }

    @Override
    public void deregister(IoHandle handle) {
        LocalChannelIoHandle unsafe = cast(handle);
        if (registeredChannels.remove(unsafe)) {
            unsafe.deregisterNow();
        }
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return LocalChannelIoHandle.class.isAssignableFrom(handleType);
    }
}

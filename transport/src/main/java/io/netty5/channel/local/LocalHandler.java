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

import io.netty5.channel.Channel;
import io.netty5.channel.IoExecutionContext;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoHandler;
import io.netty5.channel.IoHandlerFactory;
import io.netty5.util.internal.StringUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * {@link IoHandler} implementation for {@link LocalChannel} and {@link LocalServerChannel}.
 */
public final class LocalHandler implements IoHandler {
    private final Set<LocalChannelUnsafe> registeredChannels = new HashSet<>(64);
    private volatile Thread executionThread;

    private LocalHandler() { }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link LocalHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return LocalHandler::new;
    }

    private static LocalChannelUnsafe cast(IoHandle handle) {
        if (handle instanceof LocalChannelUnsafe) {
            return (LocalChannelUnsafe) handle;
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
        for (LocalChannelUnsafe unsafe : registeredChannels) {
            unsafe.closeTransportNow();
        }
        registeredChannels.clear();
    }

    @Override
    public void destroy() {
    }

    @Override
    public void register(IoHandle handle) {
        LocalChannelUnsafe unsafe = cast(handle);
        if (registeredChannels.add(unsafe)) {
            unsafe.registerTransportNow();
        }
    }

    @Override
    public void deregister(IoHandle handle) {
        LocalChannelUnsafe unsafe = cast(handle);
        if (registeredChannels.remove(unsafe)) {
            unsafe.deregisterTransportNow();
        }
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return LocalChannelUnsafe.class.isAssignableFrom(handleType);
    }
}

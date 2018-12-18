/*
 * Copyright 2018 The Netty Project
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
package io.netty.channel.local;

import io.netty.channel.Channel;
import io.netty.channel.IoExecutionContext;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.util.internal.StringUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * {@link IoHandler} implementation for {@link LocalChannel} and {@link LocalServerChannel}.
 */
public final class LocalHandler implements IoHandler {
    private final Set<LocalChannelUnsafe> registeredChannels = new HashSet<LocalChannelUnsafe>(64);
    private volatile Thread executionThread;

    private LocalHandler() { }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link LocalHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return new IoHandlerFactory() {
            @Override
            public IoHandler newHandler() {
                return new LocalHandler();
            }
        };
    }

    private static LocalChannelUnsafe cast(Channel channel) {
        Channel.Unsafe unsafe = channel.unsafe();
        if (unsafe instanceof LocalChannelUnsafe) {
            return (LocalChannelUnsafe) unsafe;
        }
        throw new IllegalArgumentException("Channel of type " + StringUtil.simpleClassName(channel) + " not supported");
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
            unsafe.close(unsafe.voidPromise());
        }
        registeredChannels.clear();
    }

    @Override
    public void destroy() {
    }

    @Override
    public void register(Channel channel) {
        LocalChannelUnsafe unsafe = cast(channel);
        if (registeredChannels.add(unsafe)) {
            unsafe.register0();
        }
    }

    @Override
    public void deregister(Channel channel) {
        LocalChannelUnsafe unsafe = cast(channel);
        if (registeredChannels.remove(unsafe)) {
            unsafe.deregister0();
        }
    }
}

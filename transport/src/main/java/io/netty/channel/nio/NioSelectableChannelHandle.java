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
package io.netty.channel.nio;


import io.netty.channel.IoHandle;
import io.netty.channel.IoOpt;
import io.netty.channel.IoRegistration;
import io.netty.util.internal.ObjectUtil;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Allows to create an {@link IoHandle} for a {@link SelectableChannel}, not necessarily created by Netty. This
 * {@link IoHandle} can be used together with {@link NioIoHandler} and so have events dispatched for
 * the {@link SelectableChannel}.
 */
public abstract class NioSelectableChannelHandle<S extends SelectableChannel> implements IoHandle, NioHandle {
    private final S channel;
    final int interestOps;

    public NioSelectableChannelHandle(S channel, int interestOps) {
        if ((interestOps & ~channel.validOps()) != 0) {
            throw new IllegalArgumentException(
                    "invalid interestOps: " + interestOps + "(validOps: " + channel.validOps() + ')');
        }
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        this.interestOps = interestOps;
    }

    @Override
    public void handle(IoRegistration registration, IoOpt ioEvent) {
        SelectionKey key = ((NioIoRegistration) registration).selectionKey();
        NioSelectableChannelHandle.this.handle(channel, key);
    }

    @Override
    public void close() throws Exception {
        channel.close();
    }

    @Override
    public SelectableChannel selectableChannel() {
        return channel;
    }

    protected abstract void handle(S channel, SelectionKey key);

    protected void deregister(S channel) {
        // NOOP.
    }
}

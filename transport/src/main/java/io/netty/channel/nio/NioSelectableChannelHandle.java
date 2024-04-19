
package io.netty.channel.nio;


import io.netty.channel.IoHandle;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
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
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * Allows to create an {@link IoHandle} for a {@link SelectableChannel}, not necessarily created by Netty. This
 * {@link IoHandle} can be used together with {@link NioHandler} and so have events dispatched for
 * the {@link SelectableChannel}.
 */
public abstract class NioSelectableChannelHandle<S extends SelectableChannel> implements IoHandle {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSelectableChannelHandle.class);

    private final S channel;
    private final int interestOps;
    private volatile SelectionKey selectionKey;

    public NioSelectableChannelHandle(S channel, int interestOps) {
        if ((interestOps & ~channel.validOps()) != 0) {
            throw new IllegalArgumentException(
                    "invalid interestOps: " + interestOps + "(validOps: " + channel.validOps() + ')');
        }
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        this.interestOps = interestOps;
    }

    @Override
    public boolean isRegistered() {
        return channel.isRegistered();
    }

    private final NioProcessor nioProcessor = new NioProcessor() {
        @Override
        public void register(Selector selector) throws ClosedChannelException {
            int interestOps;
            SelectionKey key = selectionKey;
            if (key != null) {
                interestOps = key.interestOps();
                key.cancel();
            } else {
                interestOps = NioSelectableChannelHandle.this.interestOps;
            }
            selectionKey = channel.register(selector, interestOps, this);
        }

        @Override
        public void deregister() {
            SelectionKey key = selectionKey;
            if (key != null) {
                key.cancel();
            }
        }

        @Override
        public void handle(SelectionKey key) {
            NioSelectableChannelHandle.this.handle(channel, key);
        }

        @Override
        public void close() {
            try {
                channel.close();
            } catch (IOException e) {
                logger.warn("Unexpected exception while closing underlying channel", e);
            }
        }
    };

    NioProcessor nioProcessor() {
        return nioProcessor;
    }

    protected abstract void handle(S channel, SelectionKey key);

    protected void deregister(S channel) {
        // NOOP.
    }
}

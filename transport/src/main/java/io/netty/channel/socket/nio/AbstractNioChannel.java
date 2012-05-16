/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public abstract class AbstractNioChannel extends AbstractChannel {

    private final SelectableChannel ch;
    private volatile SelectionKey selectionKey;

    protected AbstractNioChannel(Channel parent, Integer id, SelectableChannel ch) {
        super(parent, id);
        this.ch = ch;
    }

    @Override
    protected SelectableChannel javaChannel() {
        return ch;
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof SingleThreadSelectorEventLoop;
    }

    @Override
    protected void doRegister() throws Exception {
        SingleThreadSelectorEventLoop loop = (SingleThreadSelectorEventLoop) eventLoop();
        selectionKey = javaChannel().register(loop.selector, isActive()? SelectionKey.OP_READ : 0, this);
    }

    @Override
    protected boolean inEventLoopDrivenFlush() {
        return (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
    }
}

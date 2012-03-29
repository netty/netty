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

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;

public abstract class AbstractJdkChannel implements JdkChannel {

    final AbstractSelectableChannel channel;

    AbstractJdkChannel(AbstractSelectableChannel channel) {
        this.channel = channel;
    }
    
    protected AbstractSelectableChannel getChannel() {
        return channel;
    }
    
    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public SelectionKey keyFor(Selector selector) {
        return channel.keyFor(selector);
    }

    @Override
    public SelectionKey register(Selector selector, int interestedOps, Object attachment) throws ClosedChannelException {
        return channel.register(selector, interestedOps, attachment);
    }

    @Override
    public boolean isRegistered() {
        return channel.isRegistered();
    }
    

    @Override
    public void configureBlocking(boolean block) throws IOException {
        channel.configureBlocking(block);
    }
    

    @Override
    public boolean finishConnect() throws IOException {
        return true;
    }

}

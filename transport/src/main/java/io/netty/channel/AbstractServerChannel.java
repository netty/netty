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
package io.netty.channel;

import java.net.SocketAddress;
import java.util.AbstractQueue;
import java.util.Collections;
import java.util.Iterator;

/**
 * A skeletal server-side {@link Channel} implementation.  A server-side
 * {@link Channel} does not allow the following operations:
 * <ul>
 * <li>{@link #connect(SocketAddress, ChannelFuture)}</li>
 * <li>{@link #disconnect(ChannelFuture)}</li>
 * <li>{@link #flush(ChannelFuture)}</li>
 * <li>and the shortcut methods which calls the methods mentioned above
 * </ul>
 */
public abstract class AbstractServerChannel extends AbstractChannel implements ServerChannel {

    private final ChannelBufferHolder<Object> out = ChannelBufferHolders.messageBuffer(new NoopQueue());

    /**
     * Creates a new instance.
     */
    protected AbstractServerChannel(Integer id) {
        super(null, id);
    }

    @Override
    public ChannelBufferHolder<Object> out() {
        return out;
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    protected ChannelBufferHolder<Object> firstOut() {
        return out;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doFinishConnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean inEventLoopDrivenFlush() {
        return false;
    }

    private static class NoopQueue extends AbstractQueue<Object> {
        @Override
        public boolean offer(Object e) {
            return false;
        }

        @Override
        public Object poll() {
            return null;
        }

        @Override
        public Object peek() {
            return null;
        }

        @Override
        public Iterator<Object> iterator() {
            return Collections.emptyList().iterator();
        }

        @Override
        public int size() {
            return 0;
        }
    }
}
